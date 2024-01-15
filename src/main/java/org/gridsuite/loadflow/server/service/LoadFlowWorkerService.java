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
import org.gridsuite.loadflow.server.dto.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.gridsuite.loadflow.server.service.computation.AbstractWorkerService;
import org.gridsuite.loadflow.server.service.computation.ResultContext;
import org.gridsuite.loadflow.server.service.computation.ExecutionService;
import org.gridsuite.loadflow.server.service.computation.NotificationService;
import org.gridsuite.loadflow.server.service.computation.ReportService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.gridsuite.loadflow.server.service.LoadFlowRunContext.buildParameters;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService extends AbstractWorkerService<
        LoadFlowResult,
        LoadFlowRunContext,
        LoadFlowParametersInfos,
        LoadFlowResultRepository> {

    public static final String COMPUTATION_LABEL = "LoadFlow";

    public LoadFlowWorkerService(NetworkStoreService networkStoreService, NotificationService notificationService,
                                 ReportService reportService, LoadFlowResultRepository resultRepository,
                                 ExecutionService executionService, LoadflowObserver loadflowObserver,
                                 ObjectMapper objectMapper) {
        super(networkStoreService, notificationService, reportService, resultRepository, executionService, loadflowObserver, objectMapper, COMPUTATION_LABEL);
    }

    private CompletableFuture<LoadFlowResult> runLoadFlowAsync(
            Network network,
            String variantId,
            String provider,
            LoadFlowParameters params,
            Reporter reporter,
            UUID resultUuid) {
        lockRunAndCancel.lock();
        try {
            if (resultUuid != null && cancelComputationRequests.get(resultUuid) != null) {
                return null;
            }
            LoadFlow.Runner runner = LoadFlow.find(provider);
            CompletableFuture<LoadFlowResult> future = runner.runAsync(
                    network,
                    variantId != null ? variantId : VariantManagerConstants.INITIAL_VARIANT_ID,
                    executionService.getComputationManager(),
                    params,
                    reporter);
            if (resultUuid != null) {
                futures.put(resultUuid, future);
            }
            return future;
        } finally {
            lockRunAndCancel.unlock();
        }
    }

    @Override
    protected LoadFlowResult run(LoadFlowRunContext context, UUID resultUuid) throws Exception {
        LoadFlowParameters params = buildParameters(context);
        LOGGER.info("Run loadFlow...");
        Network network = observer.observe("network.load", context, () -> getNetwork(context));

        String provider = context.getProvider();
        AtomicReference<Reporter> rootReporter = new AtomicReference<>(Reporter.NO_OP);
        Reporter reporter = Reporter.NO_OP;
        if (context.getReportContext() != null) {
            final String reportType = context.getReportContext().getReportType();
            String rootReporterId = context.getReportContext().getReportName() == null ? reportType : context.getReportContext().getReportName() + "@" + reportType;
            rootReporter.set(new ReporterModel(rootReporterId, rootReporterId));
            reporter = rootReporter.get().createSubReporter(reportType, String.format("%s (%s)", reportType, provider), "providerToUse", provider);
            // Delete any previous LF computation logs
            observer.observe("report.delete", context, () -> reportService.deleteReport(context.getReportContext().getReportId(), reportType));
        }

        CompletableFuture<LoadFlowResult> future = runLoadFlowAsync(network, context.getVariantId(), provider, params, reporter, resultUuid);

        LoadFlowResult result = future == null ? null : observer.observeRun("run", context, future::get);
        if (result != null && result.isOk()) {
            // flush each network in the network store
            observer.observe("network.save", context, () -> networkStoreService.flush(network));
        }
        if (context.getReportContext().getReportId() != null) {
            observer.observe("report.send", context, () -> reportService.sendReport(context.getReportContext().getReportId(), rootReporter.get()));
        }
        return result;
    }

    @Override
    protected void saveResult(Network network, ResultContext<LoadFlowRunContext> resultContext, LoadFlowResult result) {
        List<LimitViolationInfos> limitViolationInfos = getLimitViolations(network, resultContext.getRunContext());
        List<LimitViolationInfos> limitViolationsWithCalculatedOverload = calculateOverloadLimitViolations(limitViolationInfos, network);
        resultRepository.insert(resultContext.getResultUuid(), result,
                LoadFlowService.computeLoadFlowStatus(result), limitViolationsWithCalculatedOverload);
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

    protected List<LimitViolationInfos> calculateOverloadLimitViolations(List<LimitViolationInfos> limitViolationInfos, Network network) {
        for (LimitViolationInfos violationInfo : limitViolationInfos) {
            if (violationInfo.getLimitName() != null && violationInfo.getLimitType() == LimitViolationType.CURRENT) {
                violationInfo.setActualOverloadDuration(calculateActualOverloadDuration(violationInfo, network));
                violationInfo.setUpComingOverloadDuration(calculateUpcomingOverloadDuration(violationInfo));
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
        LoadFlowParameters lfCommonParams = buildParameters(loadFlowRunContext);
        if (lfCommonParams.isDc()) {
            violations = Security.checkLimitsDc(network, loadFlowRunContext.getLimitReduction(), lfCommonParams.getDcPowerFactor());
        } else {
            violations = Security.checkLimits(network, loadFlowRunContext.getLimitReduction());
        }
        return violations.stream()
                .map(LoadFlowWorkerService::toLimitViolationInfos).toList();
    }
}
