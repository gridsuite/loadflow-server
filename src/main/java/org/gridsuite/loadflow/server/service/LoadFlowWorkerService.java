/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.criteria.AtLeastOneNominalVoltageCriterion;
import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.iidm.criteria.duration.IntervalTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.LimitDurationCriterion;
import com.powsybl.iidm.criteria.duration.PermanentDurationCriterion;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.util.LimitViolationUtils;
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
import org.gridsuite.computation.service.*;
import org.gridsuite.loadflow.server.dto.InitialValuesInfos;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.parameters.LimitReductionsByVoltageLevel;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.gridsuite.computation.utils.ComputationResultUtils.getViolationLocationId;
import static org.gridsuite.loadflow.server.service.LoadFlowService.COMPUTATION_TYPE;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService extends AbstractWorkerService<LoadFlowResult, LoadFlowRunContext, LoadFlowParametersValues, LoadFlowResultService> {
    private final LimitReductionService limitReductionService;
    public static final String HEADER_WITH_RATIO_TAP_CHANGERS = "withRatioTapChangers";

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
        InitialValuesInfos initialValuesInfos = handleSolvedValues(network, resultContext.getRunContext().isApplySolvedValues());
        List<LimitViolationInfos> limitViolationInfos = getLimitViolations(network, resultContext.getRunContext());
        List<LimitViolationInfos> limitViolationsWithCalculatedOverload = calculateOverloadLimitViolations(limitViolationInfos, network);
        resultService.insert(resultContext.getResultUuid(), result,
                LoadFlowService.computeLoadFlowStatus(result), initialValuesInfos, limitViolationsWithCalculatedOverload);
        if (result != null && !result.isFailed()) {
            // flush network in the network store
            observer.observe("network.save", resultContext.getRunContext(), () -> networkStoreService.flush(resultContext.getRunContext().getNetwork()));
        }
    }

    private InitialValuesInfos handleSolvedValues(Network network, boolean applySolvedValues) {
        if (!applySolvedValues) {
            return null;
        }
        InitialValuesInfos initialValuesInfos = new InitialValuesInfos();
        handle2WTSolvedValues(network, initialValuesInfos);
        handleSCSolvedValues(network, initialValuesInfos);
        return initialValuesInfos;
    }

    private void handle2WTSolvedValues(Network network, InitialValuesInfos initialValuesInfos) {
        network.getTwoWindingsTransformerStream()
            .forEach(t -> {
                Integer ratioTapPosition = handleSolvedTapPosition(t.getOptionalRatioTapChanger());
                Integer phaseTapPosition = handleSolvedTapPosition(t.getOptionalPhaseTapChanger());
                if (ratioTapPosition != null || phaseTapPosition != null) {
                    initialValuesInfos.add2WTTapPositionValues(t.getId(), ratioTapPosition, phaseTapPosition);
                }
            });
    }

    private Integer handleSolvedTapPosition(Optional<? extends TapChanger<?, ?, ?, ?>> tapChanger) {
        Integer initialTapPosition = null;
        boolean isSolvedValuePresent = tapChanger.isPresent() && tapChanger.get().findSolvedTapPosition().isPresent();
        if (isSolvedValuePresent && tapChanger.get().getSolvedTapPosition() != tapChanger.get().getTapPosition()) {
            initialTapPosition = tapChanger.get().getTapPosition();
            tapChanger.get().applySolvedValues();
        }
        return initialTapPosition;
    }

    private void handleSCSolvedValues(Network network, InitialValuesInfos initialValuesInfos) {
        network.getShuntCompensatorStream().forEach(shuntCompensator -> {
            if (shuntCompensator.findSolvedSectionCount().isPresent() && shuntCompensator.getSolvedSectionCount() != shuntCompensator.getSectionCount()) {
                initialValuesInfos.addSCSectionCountValue(shuntCompensator.getId(), shuntCompensator.getSectionCount());
                shuntCompensator.applySolvedValues();
            }
        });
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

    private static LoadingLimits.TemporaryLimit getNextTemporaryLimit(Branch<?> branch, LimitViolationInfos violationInfo) {
        // limits are returned from the store by DESC duration / ASC value
        Optional<CurrentLimits> currentLimits = violationInfo.getSide().equals(TwoSides.ONE.name()) ? branch.getCurrentLimits1() : branch.getCurrentLimits2();
        if (currentLimits.isEmpty()) {
            return null;
        }

        if (violationInfo.getLimitName().equals(LimitViolationUtils.PERMANENT_LIMIT_NAME)) {
            return currentLimits.get().getTemporaryLimits().stream().findFirst().orElse(null);
        }

        Iterator<LoadingLimits.TemporaryLimit> temporaryLimitIterator = currentLimits.get().getTemporaryLimits().iterator();
        while (temporaryLimitIterator.hasNext()) {
            LoadingLimits.TemporaryLimit currentTemporaryLimit = temporaryLimitIterator.next();
            if (currentTemporaryLimit.getName().equals(violationInfo.getLimitName())) {
                return temporaryLimitIterator.hasNext() ? temporaryLimitIterator.next() : null;
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
                violationInfo.setPatlLimit(getPatlLimit(violationInfo, network));
                violationInfo.setActualOverloadDuration(calculateActualOverloadDuration(violationInfo, network));
                violationInfo.setUpComingOverloadDuration(calculateUpcomingOverloadDuration(violationInfo));
                violationInfo.setNextLimitName(getNextLimitName(violationInfo, network));
                Double overload = (violationInfo.getValue() / violationInfo.getLimit()) * 100;
                violationInfo.setOverload(overload);
                if (violationInfo.getPatlLimit() != null) {
                    violationInfo.setPatlOverload((violationInfo.getValue() / violationInfo.getPatlLimit()) * 100);
                }
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

    public static Double getPatlLimit(LimitViolationInfos limitViolationInfos, Network network) {
        String equipmentId = limitViolationInfos.getSubjectId();
        Branch<?> branch = network.getBranch(equipmentId);
        if (branch == null) {
            return null;
        }

        Optional<CurrentLimits> currentLimits = limitViolationInfos.getSide().equals(TwoSides.ONE.name()) ? branch.getCurrentLimits1() : branch.getCurrentLimits2();
        if (currentLimits.isPresent()) {
            return currentLimits.get().getPermanentLimit();
        }
        return null;
    }

    public static String getNextLimitName(LimitViolationInfos limitViolationInfos, Network network) {
        String equipmentId = limitViolationInfos.getSubjectId();
        Branch<?> branch = network.getBranch(equipmentId);
        if (branch == null) {
            return null;
        }
        LoadingLimits.TemporaryLimit temporaryLimit = getNextTemporaryLimit(branch, limitViolationInfos);
        return temporaryLimit != null ? temporaryLimit.getName() : null;
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

    @Override
    protected void sendResultMessage(AbstractResultContext<LoadFlowRunContext> resultContext, LoadFlowResult ignoredResult) {
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put(HEADER_WITH_RATIO_TAP_CHANGERS, resultContext.getRunContext().isWithRatioTapChangers());
        notificationService.sendResultMessage(resultContext.getResultUuid(), resultContext.getRunContext().getReceiver(),
            resultContext.getRunContext().getUserId(), additionalData);
    }
}
