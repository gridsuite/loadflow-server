/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.Security;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultService;
import org.gridsuite.loadflow.server.computation.service.*;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.gridsuite.loadflow.server.service.LoadFlowService.COMPUTATION_TYPE;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService extends AbstractWorkerService<LoadFlowResult, LoadFlowRunContext, LoadFlowParametersValues, LoadFlowResultService> {

    public LoadFlowWorkerService(NetworkStoreService networkStoreService, NotificationService notificationService,
                                 ReportService reportService, LoadFlowResultService resultService,
                                 ExecutionService executionService, LoadFlowObserver observer,
                                 ObjectMapper objectMapper) {
        super(networkStoreService, notificationService, reportService, resultService, executionService, observer, objectMapper);
    }

    public Consumer<Message<String>> consumeRun() {
        return message -> {
            LoadFlowResultContext resultContext = LoadFlowResultContext.fromMessage(message, objectMapper);
            try {
                runRequests.add(resultContext.getResultUuid());
                AtomicReference<Long> startTime = new AtomicReference<>();
                startTime.set(System.nanoTime());

                Network network = getNetwork(resultContext.getRunContext());

                LoadFlowResult result = run(network, resultContext);

                long nanoTime = System.nanoTime();
                LOGGER.info("Just run in {}s", TimeUnit.NANOSECONDS.toSeconds(nanoTime - startTime.getAndSet(nanoTime)));

                observer.observe("results.save", resultContext.getRunContext(), () -> saveResult(network, resultContext, result));

                long finalNanoTime = System.nanoTime();
                LOGGER.info("Stored in {}s", TimeUnit.NANOSECONDS.toSeconds(finalNanoTime - startTime.getAndSet(finalNanoTime)));

                if (result != null) {  // result available
                    notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver());
                    LOGGER.info("{} complete (resultUuid='{}')", getComputationType(), resultContext.getResultUuid());
                } else {  // result not available : stop computation request
                    if (cancelComputationRequests.get(resultContext.getResultUuid()) != null) {
                        cleanResultsAndPublishCancel(resultContext.getResultUuid(), cancelComputationRequests.get(resultContext.getResultUuid()).getReceiver());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!(e instanceof CancellationException)) {
                    LOGGER.error(NotificationService.getFailedMessage(getComputationType()), e);
                    notificationService.publishFail(
                            resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(),
                            e.getMessage(), resultContext.getRunContext().getUserId(), getComputationType());
                    resultService.delete(resultContext.getResultUuid());
                }
            } finally {
                futures.remove(resultContext.getResultUuid());
                cancelComputationRequests.remove(resultContext.getResultUuid());
                runRequests.remove(resultContext.getResultUuid());
            }
        };
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }

    protected LoadFlowResult run(Network network, AbstractResultContext<LoadFlowRunContext> resultContext) throws Exception {
        LOGGER.info("Run {} computation ...", getComputationType());

        LoadFlowRunContext context = resultContext.getRunContext();
        String provider = context.getProvider();
        AtomicReference<Reporter> rootReporter = new AtomicReference<>(Reporter.NO_OP);
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportContext().getReportId() != null) {
            final String reportType = context.getReportContext().getReportType();
            String rootReporterId = context.getReportContext().getReportName() == null ? reportType : context.getReportContext().getReportName() + "@" + reportType;
            rootReporter.set(new ReporterModel(rootReporterId, rootReporterId));
            reporter = rootReporter.get().createSubReporter(reportType, String.format("%s (%s)", reportType, provider), "providerToUse", provider);
            // Delete any previous computation logs
            observer.observe("report.delete", context, () -> reportService.deleteReport(context.getReportContext().getReportId(), reportType));
        }

        CompletableFuture<LoadFlowResult> future = runAsync(network, context, provider, reporter, resultContext.getResultUuid());

        LoadFlowResult result = future == null ? null : observer.observeRun("run", context, future::get);
        if (context.getReportContext().getReportId() != null) {
            observer.observe("report.send", context, () -> reportService.sendReport(context.getReportContext().getReportId(), rootReporter.get()));
        }
        if (result != null && !result.isFailed()) {
            // flush each network in the network store
            observer.observe("network.save", resultContext.getRunContext(), () -> networkStoreService.flush(network));
        }
        return result;
    }

    protected CompletableFuture<LoadFlowResult> runAsync(
            Network network,
            LoadFlowRunContext runContext,
            String provider,
            Reporter reporter,
            UUID resultUuid) {
        lockRunAndCancel.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }
            CompletableFuture<LoadFlowResult> future = getCompletableFuture(network, runContext, provider, reporter);
            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancel.unlock();
        }
    }

    protected CompletableFuture<LoadFlowResult> getCompletableFuture(Network network, LoadFlowRunContext runContext, String provider, Reporter reporter) {
        LoadFlowParameters params = runContext.buildParameters();
        LoadFlow.Runner runner = LoadFlow.find(provider);
        return runner.runAsync(
                network,
                runContext.getVariantId() != null ? runContext.getVariantId() : VariantManagerConstants.INITIAL_VARIANT_ID,
                executionService.getComputationManager(),
                params,
                reporter);
    }

    protected void saveResult(Network network, AbstractResultContext<LoadFlowRunContext> resultContext, LoadFlowResult result) {
        List<LimitViolationInfos> limitViolationInfos = getLimitViolations(network, resultContext.getRunContext());
        List<LimitViolationInfos> limitViolationsWithCalculatedOverload = calculateOverloadLimitViolations(limitViolationInfos, network);
        resultService.insert(resultContext.getResultUuid(), result,
                LoadFlowService.computeLoadFlowStatus(result), limitViolationsWithCalculatedOverload);
    }

    private static LoadingLimits.TemporaryLimit getBranchLimitViolationAboveCurrentValue(Branch<?> branch, LimitViolationInfos violationInfo) {
        // limits are returned from the store by DESC duration / ASC value
        Optional<CurrentLimits> currentLimits = violationInfo.getSide().equals(TwoSides.ONE.name()) ? branch.getCurrentLimits1() : branch.getCurrentLimits2();
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

    protected List<LimitViolationInfos> calculateOverloadLimitViolations(List<LimitViolationInfos> limitViolationInfos, Network network) {
        for (LimitViolationInfos violationInfo : limitViolationInfos) {
            if (violationInfo.getLimitName() != null && violationInfo.getLimitType() == LimitViolationType.CURRENT
                    && violationInfo.getValue() != null && violationInfo.getLimit() != null) {
                violationInfo.setActualOverloadDuration(calculateActualOverloadDuration(violationInfo, network));
                violationInfo.setUpComingOverloadDuration(calculateUpcomingOverloadDuration(violationInfo));
                Double overload = (violationInfo.getValue() / violationInfo.getLimit()) * 100;
                violationInfo.setOverload(overload);
            }
        }
        return limitViolationInfos;
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
        LoadFlowParameters lfCommonParams = loadFlowRunContext.buildParameters();
        if (lfCommonParams.isDc()) {
            violations = Security.checkLimitsDc(network, loadFlowRunContext.getLimitReduction(), lfCommonParams.getDcPowerFactor());
        } else {
            violations = Security.checkLimits(network, loadFlowRunContext.getLimitReduction());
        }
        return violations.stream()
                .map(LoadFlowWorkerService::toLimitViolationInfos).toList();
    }
}
