/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.Reporter;
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
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.gridsuite.loadflow.server.service.computation.*;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.gridsuite.loadflow.server.service.LoadFlowService.COMPUTATION_TYPE;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService extends AbstractWorkerService<LoadFlowResult, LoadFlowRunContext, LoadFlowParametersValues> {

    public LoadFlowWorkerService(NetworkStoreService networkStoreService, NotificationService notificationService,
                                 ReportService reportService, LoadFlowResultRepository resultRepository,
                                 ExecutionService executionService, LoadFlowObserver loadflowObserver,
                                 ObjectMapper objectMapper) {
        super(networkStoreService, notificationService, reportService, resultRepository, executionService, loadflowObserver, objectMapper);
    }

    private LoadFlowResultRepository getResultRepository() {
        return (LoadFlowResultRepository) resultRepository;
    }

    @Override
    protected String getComputationType() {
        return COMPUTATION_TYPE;
    }

    @Override
    protected LoadFlowResultContext fromMessage(Message<String> message) {
        return LoadFlowResultContext.fromMessage(message, objectMapper);
    }

    @Override
    protected LoadFlowResult run(Network network, AbstractResultContext<LoadFlowRunContext> resultContext) throws Exception {
        LoadFlowResult result = super.run(network, resultContext);
        if (result != null && result.isOk()) {
            // flush each network in the network store
            observer.observe("network.save", resultContext.getRunContext(), () -> networkStoreService.flush(network));
        }
        return result;
    }

    @Override
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

    @Override
    protected void saveResult(Network network, AbstractResultContext<LoadFlowRunContext> resultContext, LoadFlowResult result) {
        List<LimitViolationInfos> limitViolationInfos = getLimitViolations(network, resultContext.getRunContext());
        List<LimitViolationInfos> limitViolationsWithCalculatedOverload = calculateOverloadLimitViolations(limitViolationInfos, network);
        getResultRepository().insert(resultContext.getResultUuid(), result,
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
