/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.Security;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.gridsuite.loadflow.server.service.LoadFlowRunContext.buildParameters;
import static org.gridsuite.loadflow.server.service.NotificationService.FAIL_MESSAGE;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowWorkerService.class);

    private Lock lockRunAndCancelLF = new ReentrantLock();

    private ObjectMapper objectMapper;

    private Set<UUID> runRequests = Sets.newConcurrentHashSet();
    private NetworkStoreService networkStoreService;
    private ReportService reportService;

    NotificationService notificationService;

    private LoadFlowResultRepository resultRepository;

    public LoadFlowWorkerService(NetworkStoreService networkStoreService, NotificationService notificationService, ReportService reportService,
                                 LoadFlowResultRepository resultRepository, ObjectMapper objectMapper) {
        this.networkStoreService = networkStoreService;
        this.notificationService = notificationService;
        this.reportService = reportService;
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
    }

    private Map<UUID, CompletableFuture<LoadFlowResult>> futures = new ConcurrentHashMap<>();

    private Map<UUID, LoadFlowCancelContext> cancelComputationRequests = new ConcurrentHashMap<>();

    private Network getNetwork(UUID networkUuid, String variantId) {
        Network network;
        try {
            network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW);
            String variant = StringUtils.isBlank(variantId) ? VariantManagerConstants.INITIAL_VARIANT_ID : variantId;
            network.getVariantManager().setWorkingVariant(variant);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
        return network;
    }

    private CompletableFuture<LoadFlowResult> runLoadFlowAsync(
            Network network,
            String variantId,
            String provider,
            LoadFlowParameters params,
            Reporter reporter,
            UUID resultUuid) {
        lockRunAndCancelLF.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }
            LoadFlow.Runner runner = LoadFlow.find(provider);
            CompletableFuture<LoadFlowResult> future = runner.runAsync(network, variantId != null ? variantId : VariantManagerConstants.INITIAL_VARIANT_ID, LocalComputationManager.getDefault(), params, reporter);
            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancelLF.unlock();
        }
    }

    public LoadFlowResult run(Network network, LoadFlowRunContext context, UUID resultUuid) throws ExecutionException, InterruptedException {
        LoadFlowParameters params = buildParameters(context.getParameters(), context.getProvider());
        LOGGER.info("Run loadFlow...");

        String provider = context.getProvider();

        Reporter rootReporter = Reporter.NO_OP;
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportContext() != null) {
            final String reportType = context.getReportContext().getReportType();
            String rootReporterId = context.getReportContext().getReportName() == null ? reportType : context.getReportContext().getReportName() + "@" + reportType;
            rootReporter = new ReporterModel(rootReporterId, rootReporterId);
            reporter = rootReporter.createSubReporter(reportType, String.format("%s (%s)", reportType, provider), "providerToUse", provider);
            // Delete any previous LF computation logs
            reportService.deleteReport(context.getReportContext().getReportId(), reportType);
        }

        CompletableFuture<LoadFlowResult> future = runLoadFlowAsync(network, context.getVariantId(), context.getProvider(), params, reporter, resultUuid);

        LoadFlowResult result = future == null ? null : future.get();
        if (result != null && result.isOk()) {
            // flush each network in the network store
            networkStoreService.flush(network);
        }
        if (context.getReportContext().getReportId() != null) {
            reportService.sendReport(context.getReportContext().getReportId(), rootReporter);
        }
        return result;
    }

    private void cleanLoadFlowResultsAndPublishCancel(UUID resultUuid, String receiver) {
        resultRepository.delete(resultUuid);
        notificationService.publishStop(resultUuid, receiver);
    }

    private static LoadingLimits.TemporaryLimit getBranchLimitViolationAboveCurrentValue(Branch<?> branch, LimitViolationInfos violationInfo) {
        // limits are returned from the store by DESC duration / ASC value
        Optional<CurrentLimits> currentLimits = violationInfo.getSide().equals(Branch.Side.ONE.name()) ? branch.getCurrentLimits1() : branch.getCurrentLimits2();
        if (!currentLimits.isPresent() || violationInfo.getValue() < currentLimits.get().getPermanentLimit()) {
            return null;
        } else {
            Optional<LoadingLimits.TemporaryLimit> nextTemporaryLimit = currentLimits.get().getTemporaryLimits().stream()
                          .filter(tl -> violationInfo.getValue() < tl.getValue())
                            .findFirst();
            if (nextTemporaryLimit.isPresent()) {
                return nextTemporaryLimit.get();
            }
        }
        return null;
    }

    public static Integer calculateUpcomingOverloadDuration(LimitViolationInfos limitViolationInfo) {
        if (limitViolationInfo.getValue() < limitViolationInfo.getLimit()) {
            return limitViolationInfo.getUpComingOverloadDuration();
        }
        return null;
    }

    public static Integer calculateActualOverloadDuration(LimitViolationInfos limitViolationInfo, Network network) {
        if (limitViolationInfo.getValue() > limitViolationInfo.getLimit()) {
            return limitViolationInfo.getActualOverloadDuration();
        } else {
            String equipmentId = limitViolationInfo.getSubjectId();
            LoadingLimits.TemporaryLimit tempLimit = null;

            Line line = network.getLine(equipmentId);
            if (line != null) {
                tempLimit = getBranchLimitViolationAboveCurrentValue(line, limitViolationInfo);
            } else {
                TwoWindingsTransformer twoWindingsTransformer = network.getTwoWindingsTransformer(equipmentId);
                if (twoWindingsTransformer != null) {
                    tempLimit = getBranchLimitViolationAboveCurrentValue(twoWindingsTransformer, limitViolationInfo);
                }
            }
            return (tempLimit != null) ? tempLimit.getAcceptableDuration() : null;
        }
    }

    private List<LimitViolationInfos> calculateOverloadLimitViolations(List<LimitViolationInfos> limitViolationInfos, Network network) {
        for (LimitViolationInfos violationInfo : limitViolationInfos) {
            if (violationInfo.getLimitName() != null && violationInfo.getLimitType() == LimitViolationType.CURRENT) {
                violationInfo.setActualOverloadDuration(calculateActualOverloadDuration(violationInfo, network));
                violationInfo.setUpComingOverloadDuration(calculateUpcomingOverloadDuration(violationInfo));
            }
        }
        return limitViolationInfos;
    }

    private void cancelLoadFlowAsync(LoadFlowCancelContext cancelContext) {
        lockRunAndCancelLF.lock();
        try {
            cancelComputationRequests.put(cancelContext.getResultUuid(), cancelContext);

            // find the completableFuture associated with result uuid
            CompletableFuture<LoadFlowResult> future = futures.get(cancelContext.getResultUuid());
            if (future != null) {
                future.cancel(true);  // cancel computation in progress
            }
            cleanLoadFlowResultsAndPublishCancel(cancelContext.getResultUuid(), cancelContext.getReceiver());
        } finally {
            lockRunAndCancelLF.unlock();
        }
    }

    public static LimitViolationInfos toLimitViolationInfos(LimitViolation violation) {
        return LimitViolationInfos.builder()
            .subjectId(violation.getSubjectId())
            .actualOverloadDuration(violation.getAcceptableDuration())
            .upComingOverloadDuration(violation.getAcceptableDuration())
            .limit(violation.getLimit())
            .limitName(violation.getLimitName())
            .value(violation.getValue())
            .side(violation.getSide() != null ? violation.getSide().name() : "")
            .limitType(violation.getLimitType()).build();
    }

    private List<LimitViolationInfos> getLimitViolations(Network network, LoadFlowRunContext loadFlowRunContext) {
        List<LimitViolation> violations;
        LoadFlowParameters lfCommonParams = buildParameters(loadFlowRunContext.getParameters(), loadFlowRunContext.getProvider());
        if (lfCommonParams.isDc()) {
            violations = Security.checkLimitsDc(network, loadFlowRunContext.getLimitReduction(), lfCommonParams.getDcPowerFactor());
        } else {
            violations = Security.checkLimits(network, loadFlowRunContext.getLimitReduction());
        }
        return violations.stream()
            .map(LoadFlowWorkerService::toLimitViolationInfos).toList();
    }

    @Bean
    public Consumer<Message<String>> consumeRun() {
        return message -> {
            LoadFlowResultContext resultContext = LoadFlowResultContext.fromMessage(message, objectMapper);
            try {
                runRequests.add(resultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();

                startTime.set(System.nanoTime());
                Network network = getNetwork(resultContext.getRunContext().getNetworkUuid(), resultContext.getRunContext().getVariantId());

                LoadFlowResult result = run(network, resultContext.getRunContext(), resultContext.getResultUuid());
                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                List<LimitViolationInfos> limitViolationInfos = getLimitViolations(network, resultContext.getRunContext());
                List<LimitViolationInfos> limitViolationsWithCalculatedOverload = calculateOverloadLimitViolations(limitViolationInfos, network);
                resultRepository.insert(resultContext.getResultUuid(), result, LoadFlowService.computeLoadFlowStatus(result), limitViolationsWithCalculatedOverload);
                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver());
                    LOGGER.info("LoadFlow complete (resultUuid='{}')", resultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanLoadFlowResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error(FAIL_MESSAGE, e);
                if (!(e instanceof CancellationException)) {
                    notificationService.publishFail(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(), e.getMessage(), resultContext.getRunContext().getUserId());
                    resultRepository.delete(resultContext.getResultUuid());
                }
            } finally {
                futures.remove(resultContext.getResultUuid());
                cancelComputationRequests.remove(resultContext.getResultUuid());
                runRequests.remove(resultContext.getResultUuid());
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeCancel() {
        return message -> cancelLoadFlowAsync(LoadFlowCancelContext.fromMessage(message));
    }

}
