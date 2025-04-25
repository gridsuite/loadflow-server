/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.criteria.AtLeastOneNominalVoltageCriterion;
import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.iidm.criteria.duration.IntervalTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.LimitDurationCriterion;
import com.powsybl.iidm.criteria.duration.PermanentDurationCriterion;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.Security;
import com.powsybl.security.limitreduction.DefaultLimitReductionsApplier;
import com.powsybl.security.limitreduction.LimitReduction;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.parameters.LimitReductionsByVoltageLevel;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import com.powsybl.ws.commons.computation.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.powsybl.ws.commons.computation.utils.ComputationResultUtils.getViolationLocationId;
import static org.gridsuite.loadflow.server.service.LoadFlowService.COMPUTATION_TYPE;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService extends AbstractWorkerService<LoadFlowResult, LoadFlowRunContext, LoadFlowParametersValues, LoadFlowResultService> {
    private final LimitReductionService limitReductionService;
    public static final String DEFAULT_PROVIDER = "OpenLoadFlow";

    public LoadFlowWorkerService(NetworkStoreService networkStoreService, NotificationService notificationService,
                                 ReportService reportService, LoadFlowResultService resultService,
                                 ExecutionService executionService, LoadFlowObserver observer,
                                 ObjectMapper objectMapper, LimitReductionService limitReductionService) {
        super(networkStoreService, notificationService, reportService, resultService, executionService, observer, objectMapper);
        this.limitReductionService = limitReductionService;
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
    protected LoadFlowResult run(LoadFlowRunContext runContext, UUID resultUuid, AtomicReference<ReportNode> rootReporter) {
        LoadFlowResult result = super.run(runContext, resultUuid, rootReporter);
        if (result != null && !result.isFailed()) {
            // flush network in the network store
            observer.observe("network.save", runContext, () -> networkStoreService.flush(runContext.getNetwork()));
        }
        return result;
    }

    @Override
    protected CompletableFuture<LoadFlowResult> getCompletableFuture(LoadFlowRunContext runContext, String provider, UUID resultUuid) {
        LoadFlowParameters params = runContext.buildParameters();
        LoadFlow.Runner runner = LoadFlow.find(provider);
        return runner.runAsync(
                runContext.getNetwork(),
                runContext.getVariantId() != null ? runContext.getVariantId() : VariantManagerConstants.INITIAL_VARIANT_ID,
                executionService.getComputationManager(),
                params,
                runContext.getReportNode());
    }

    private LimitReduction createLimitReduction(IdentifiableCriterion voltageLevelCriterion, LimitDurationCriterion limitDurationCriterion, double value) {
        return LimitReduction.builder(LimitType.CURRENT, value)
                .withNetworkElementCriteria(voltageLevelCriterion)
                .withLimitDurationCriteria(limitDurationCriterion)
                .build();
    }

    private List<LimitReduction> createLimitReductions(LoadFlowRunContext runContext) {
        List<LimitReduction> limitReductions = new ArrayList<>(limitReductionService.getVoltageLevels().size() * limitReductionService.getLimitDurations().size());

        runContext.getParameters().getLimitReductions().forEach(limitReduction -> {
            LimitReductionsByVoltageLevel.VoltageLevel voltageLevel = limitReduction.getVoltageLevel();
            IdentifiableCriterion voltageLevelCriterion = new IdentifiableCriterion(new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(voltageLevel.getLowBound(), voltageLevel.getHighBound(), false, true)));
            limitReductions.add(createLimitReduction(voltageLevelCriterion, new PermanentDurationCriterion(), limitReduction.getPermanentLimitReduction()));
            limitReduction.getTemporaryLimitReductions().forEach(temporaryLimitReduction -> {
                LimitDurationCriterion limitDurationCriterion;
                LimitReductionsByVoltageLevel.LimitDuration limitDuration = temporaryLimitReduction.getLimitDuration();
                if (temporaryLimitReduction.getLimitDuration().getHighBound() != null) {
                    limitDurationCriterion = IntervalTemporaryDurationCriterion.between(limitDuration.getLowBound(), limitDuration.getHighBound(), limitDuration.isLowClosed(), limitDuration.isHighClosed());
                } else {
                    limitDurationCriterion = IntervalTemporaryDurationCriterion.greaterThan(limitDuration.getLowBound(), limitDuration.isLowClosed());
                }
                limitReductions.add(createLimitReduction(voltageLevelCriterion, limitDurationCriterion, temporaryLimitReduction.getReduction()));
            });
        });

        return limitReductions;
    }

    @Override
    protected PreloadingStrategy getNetworkPreloadingStrategy() {
        return PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW;
    }

    @Override
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

    public static LimitViolationInfos toLimitViolationInfos(LimitViolation violation, Network network) {
        return LimitViolationInfos.builder()
                .subjectId(violation.getSubjectId())
                .locationId(getViolationLocationId(violation, network))
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
            if (limitReductionService.getProviders().contains(loadFlowRunContext.getProvider())) {
                violations = Security.checkLimitsDc(network, new DefaultLimitReductionsApplier(createLimitReductions(loadFlowRunContext)), lfCommonParams.getDcPowerFactor());

            } else {
                violations = Security.checkLimitsDc(network, loadFlowRunContext.getParameters().getLimitReduction(), lfCommonParams.getDcPowerFactor());
            }
        } else {
            if (limitReductionService.getProviders().contains(loadFlowRunContext.getProvider())) {
                violations = Security.checkLimits(network, new DefaultLimitReductionsApplier(createLimitReductions(loadFlowRunContext)));
            } else {
                violations = Security.checkLimits(network, loadFlowRunContext.getParameters().getLimitReduction());
            }

        }
        return violations.stream()
                .map(limitViolation -> toLimitViolationInfos(limitViolation, network)).toList();
    }

    @Bean
    @Override
    public Consumer<Message<String>> consumeRun() {
        return super.consumeRun();
    }

    @Bean
    @Override
    public Consumer<Message<String>> consumeCancel() {
        return super.consumeCancel();
    }
}
