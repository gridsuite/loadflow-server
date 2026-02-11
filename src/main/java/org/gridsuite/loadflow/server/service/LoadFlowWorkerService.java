/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.balances_adjustment.util.BorderBasedCountryArea;
import com.powsybl.balances_adjustment.util.CountryAreaFactory;
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
import com.powsybl.loadflow.LoadFlowRunParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.Security;
import com.powsybl.security.limitreduction.DefaultLimitReductionsApplier;
import com.powsybl.security.limitreduction.LimitReduction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.computation.service.*;
import org.gridsuite.loadflow.server.dto.CountryAdequacy;
import org.gridsuite.loadflow.server.dto.Exchange;
import org.gridsuite.loadflow.server.PropertyServerNameProvider;
import org.gridsuite.loadflow.server.dto.modifications.LoadFlowModificationInfos;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.modifications.TapPositionType;
import org.gridsuite.loadflow.server.dto.parameters.LimitReductionsByVoltageLevel;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.gridsuite.computation.utils.ComputationResultUtils.getViolationLocationId;
import static org.gridsuite.loadflow.server.service.LoadFlowService.COMPUTATION_TYPE;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public class LoadFlowWorkerService extends AbstractWorkerService<LoadFlowResult, LoadFlowRunContext, LoadFlowParametersValues, LoadFlowResultService> {
    private final LimitReductionService limitReductionService;
    public static final String HEADER_WITH_RATIO_TAP_CHANGERS = "withRatioTapChangers";

    @Setter
    @Getter
    @AllArgsConstructor
    public static class ComponentCalculatedInfos {
        private double consumptions;
        private double generations;
        private double exchanges;
        private double losses;
    }

    public record ComponentValue(int connectedComponentNum, int synchronousComponentNum, double value) { }

    public record BranchInfos(Country country1, double p1, Country country2, double p2) { }

    public LoadFlowWorkerService(NetworkStoreService networkStoreService, NotificationService notificationService,
                                 ReportService reportService, LoadFlowResultService resultService,
                                 ExecutionService executionService, LoadFlowObserver observer,
                                 ObjectMapper objectMapper, LimitReductionService limitReductionService,
                                 PropertyServerNameProvider propertyServerNameProvider) {
        super(networkStoreService, notificationService, reportService, resultService, executionService, observer, objectMapper, propertyServerNameProvider);
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
                new LoadFlowRunParameters().setComputationManager(executionService.getComputationManager()).setParameters(params).setReportNode(runContext.getReportNode())
        );
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
    public void saveResult(Network network, AbstractResultContext<LoadFlowRunContext> resultContext, LoadFlowResult result) {
        LoadFlowModificationInfos loadFlowModificationInfos = handleSolvedValues(network, resultContext.getRunContext().isApplySolvedValues());
        List<LimitViolationInfos> limitViolationInfos = getLimitViolations(network, resultContext.getRunContext());
        List<LimitViolationInfos> limitViolationsWithCalculatedOverload = calculateOverloadLimitViolations(limitViolationInfos, network);

        Map<Country, BorderBasedCountryArea> borderBasedCountryAreas = createBorderBasedCountryAreas(network);
        Map<Pair<Integer, Integer>, ComponentCalculatedInfos> componentInfos = calculateComponentInfos(network);
        List<CountryAdequacy> countryAdequacies = calculateCountryAdequacies(network, borderBasedCountryAreas);
        Map<String, List<Exchange>> exchanges = calculateExchanges(network, borderBasedCountryAreas);

        resultService.insert(resultContext.getResultUuid(), result, LoadFlowService.computeLoadFlowStatus(result),
            loadFlowModificationInfos, limitViolationsWithCalculatedOverload, componentInfos, countryAdequacies, exchanges);
        if (result != null && !result.isFailed()) {
            // flush network in the network store
            observer.observe("network.save", resultContext.getRunContext(), () -> networkStoreService.flush(resultContext.getRunContext().getNetwork()));
        }
    }

    private LoadFlowModificationInfos handleSolvedValues(Network network, boolean applySolvedValues) {
        if (!applySolvedValues) {
            return null;
        }
        LoadFlowModificationInfos loadFlowModificationInfos = new LoadFlowModificationInfos();
        handle2WTSolvedValues(network, loadFlowModificationInfos);
        handleSCSolvedValues(network, loadFlowModificationInfos);
        return loadFlowModificationInfos;
    }

    private void handle2WTSolvedValues(Network network, LoadFlowModificationInfos loadFlowModificationInfos) {
        network.getTwoWindingsTransformerStream()
            .forEach(t -> {
                Integer initialRatioTapPosition = handleSolvedTapPosition(t.getOptionalRatioTapChanger());
                if (initialRatioTapPosition != null) {
                    loadFlowModificationInfos.add2WTTapPositionValues(t.getId(), initialRatioTapPosition, t.getRatioTapChanger().getSolvedTapPosition(), TapPositionType.RATIO_TAP);
                }
                Integer initialPhaseTapPosition = handleSolvedTapPosition(t.getOptionalPhaseTapChanger());
                if (initialPhaseTapPosition != null) {
                    loadFlowModificationInfos.add2WTTapPositionValues(t.getId(), initialPhaseTapPosition, t.getPhaseTapChanger().getSolvedTapPosition(), TapPositionType.PHASE_TAP);
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

    private void handleSCSolvedValues(Network network, LoadFlowModificationInfos loadFlowModificationInfos) {
        network.getShuntCompensatorStream().forEach(shuntCompensator -> {
            if (shuntCompensator.findSolvedSectionCount().isPresent() && shuntCompensator.getSolvedSectionCount() != shuntCompensator.getSectionCount()) {
                loadFlowModificationInfos.addSCSectionCountValue(shuntCompensator.getId(), shuntCompensator.getSectionCount(), shuntCompensator.getSolvedSectionCount());
                shuntCompensator.applySolvedValues();
            }
        });
    }

    private static LoadingLimits.TemporaryLimit getBranchLimitViolationAboveCurrentValue(Branch<?> branch, LimitViolationInfos violationInfo) {
        // limits are returned from the store by DESC duration / ASC value
        Optional<CurrentLimits> currentLimits = branch.getCurrentLimits(TwoSides.valueOf(violationInfo.getSide()));
        if (currentLimits.isEmpty() || violationInfo.getValue() < currentLimits.get().getPermanentLimit()) {
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
        Optional<CurrentLimits> currentLimits = branch.getCurrentLimits(TwoSides.valueOf(violationInfo.getSide()));
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

            Branch<?> branch = network.getBranch(equipmentId);
            if (branch != null) {
                tempLimit = getBranchLimitViolationAboveCurrentValue(branch, limitViolationInfo);
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

    private ComponentValue getValueFromTerminalInComponent(Terminal terminal) {
        ComponentValue res = null;
        if (terminal != null && terminal.isConnected()) {
            Bus bus = terminal.getBusView().getBus();
            if (bus != null) {
                res = new ComponentValue(bus.getConnectedComponent().getNum(), bus.getSynchronousComponent().getNum(), zeroIfNan(terminal.getP()));
            }
        }
        return res;
    }

    private Map<Country, BorderBasedCountryArea> createBorderBasedCountryAreas(Network network) {
        Map<Country, BorderBasedCountryArea> result = new EnumMap<>(Country.class);
        Set<Country> countries = network.getSubstationStream().map(Substation::getCountry).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
        countries.forEach(country -> {
            CountryAreaFactory countryAreaFactory = new CountryAreaFactory(country);
            BorderBasedCountryArea borderBasedCountryArea = (BorderBasedCountryArea) countryAreaFactory.create(network);
            result.put(country, borderBasedCountryArea);
        });
        return result;
    }

    private static double zeroIfNan(double value) {
        return Double.isNaN(value) ? 0 : value;
    }

    private Map<Pair<Integer, Integer>, ComponentCalculatedInfos> calculateComponentInfos(Network network) {
        Map<Pair<Integer, Integer>, ComponentCalculatedInfos> result = new HashMap<>();

        // load computation by connected/synchronous component
        network.getLoads().forEach(load -> {
            Terminal terminal = load.getTerminal();
            ComponentValue componentValue = getValueFromTerminalInComponent(terminal);
            if (componentValue != null) {
                ComponentCalculatedInfos infos = result.computeIfAbsent(Pair.of(componentValue.connectedComponentNum, componentValue.synchronousComponentNum), key -> new ComponentCalculatedInfos(0., 0., 0., 0.));
                infos.setConsumptions(infos.getConsumptions() + componentValue.value);
            }
        });

        // generation computation by connected/synchronous component
        Stream.concat(network.getGeneratorStream(), network.getBatteryStream()).forEach(injection -> {
            Terminal terminal = injection.getTerminal();
            ComponentValue componentValue = getValueFromTerminalInComponent(terminal);
            if (componentValue != null) {
                ComponentCalculatedInfos infos = result.computeIfAbsent(Pair.of(componentValue.connectedComponentNum, componentValue.synchronousComponentNum), key -> new ComponentCalculatedInfos(0., 0., 0., 0.));
                infos.setGenerations(infos.getGenerations() + componentValue.value);
            }
        });

        // exchanges computation by connected/synchronous component
        network.getHvdcLineStream().forEach(hvdcLine -> {
            Pair<Terminal, Terminal> terminals = getTerminalsFromIdentifiable(hvdcLine);
            ComponentValue componentValue1 = getValueFromTerminalInComponent(terminals.getLeft());
            ComponentValue componentValue2 = getValueFromTerminalInComponent(terminals.getRight());

            if (componentValue1 != null && componentValue2 != null) {
                ComponentCalculatedInfos infos1 = result.computeIfAbsent(Pair.of(componentValue1.connectedComponentNum, componentValue1.synchronousComponentNum), key -> new ComponentCalculatedInfos(0., 0., 0., 0.));
                ComponentCalculatedInfos infos2 = result.computeIfAbsent(Pair.of(componentValue2.connectedComponentNum, componentValue2.synchronousComponentNum), key -> new ComponentCalculatedInfos(0., 0., 0., 0.));

                if (componentValue1.connectedComponentNum != componentValue2.connectedComponentNum ||
                    componentValue1.synchronousComponentNum != componentValue2.synchronousComponentNum) {
                    infos1.setExchanges(infos1.getExchanges() + componentValue1.value);
                    infos2.setExchanges(infos2.getExchanges() + componentValue2.value);
                }
            }
        });

        // reverse sign of generations and compute losses by connected/synchronous component
        result.forEach((key, value) -> {
            value.setGenerations(-value.generations);
            value.setLosses(value.getGenerations() - value.getConsumptions() - value.getExchanges());
        });
        return result;
    }

    private void fillCountryAdequacy(Map<String, CountryAdequacy> adequaciesByCountry, Country country, CountryAdequacy.ValueType valueType, double p) {
        CountryAdequacy countryAdequacy = adequaciesByCountry.computeIfAbsent(country.name(), key -> new CountryAdequacy(null, country.name(), 0., 0., 0., 0.));
        switch (valueType) {
            case LOAD -> countryAdequacy.setLoad(countryAdequacy.getLoad() + p);
            case GENERATION -> countryAdequacy.setGeneration(countryAdequacy.getGeneration() + p);
            case LOSSES -> countryAdequacy.setLosses(countryAdequacy.getLosses() + p);
            case NET_POSITION -> countryAdequacy.setNetPosition(countryAdequacy.getNetPosition() + p);
            default -> throw new IllegalStateException("Unexpected value: " + valueType);
        }
    }

    private Pair<Terminal, Terminal> getTerminalsFromIdentifiable(Identifiable<?> identifiable) {
        Terminal terminal1 = null;
        Terminal terminal2 = null;
        if (identifiable instanceof Branch<?> branch) {
            terminal1 = branch.getTerminal1();
            terminal2 = branch.getTerminal2();
        } else if (identifiable instanceof HvdcLine hvdcLine) {
            terminal1 = hvdcLine.getConverterStation1().getTerminal();
            terminal2 = hvdcLine.getConverterStation2().getTerminal();
        }
        return Pair.of(terminal1, terminal2);
    }

    private BranchInfos getBranchInfos(Identifiable<?> identifiable) {
        Pair<Terminal, Terminal> terminals = getTerminalsFromIdentifiable(identifiable);
        Pair<Country, Double> countryAndActivePowerFromTerminal1 = getCountryAndActivePowerFromTerminalInMainComponent(terminals.getLeft());
        Pair<Country, Double> countryAndActivePowerFromTerminal2 = getCountryAndActivePowerFromTerminalInMainComponent(terminals.getRight());

        return new BranchInfos(countryAndActivePowerFromTerminal1.getLeft(), countryAndActivePowerFromTerminal1.getRight(),
                               countryAndActivePowerFromTerminal2.getLeft(), countryAndActivePowerFromTerminal2.getRight());
    }

    private List<CountryAdequacy> calculateCountryAdequacies(Network network, Map<Country, BorderBasedCountryArea> borderBasedCountryAreas) {
        Map<String, CountryAdequacy> adequaciesByCountry = new HashMap<>();

        // load computation by country
        network.getLoads().forEach(load -> {
            Terminal terminal = load.getTerminal();
            Pair<Country, Double> countryAndActivePowerFromTerminal = getCountryAndActivePowerFromTerminalInMainComponent(terminal);
            Country country = countryAndActivePowerFromTerminal.getLeft();
            if (country != null) {
                double p = countryAndActivePowerFromTerminal.getRight();
                fillCountryAdequacy(adequaciesByCountry, country, CountryAdequacy.ValueType.LOAD, p);
            }
        });

        // generation computation by country
        Stream.concat(network.getGeneratorStream(), network.getBatteryStream()).forEach(injection -> {
            Terminal terminal = injection.getTerminal();
            Pair<Country, Double> countryAndActivePowerFromTerminal = getCountryAndActivePowerFromTerminalInMainComponent(terminal);
            Country country = countryAndActivePowerFromTerminal.getLeft();
            if (country != null) {
                double p = countryAndActivePowerFromTerminal.getRight();
                fillCountryAdequacy(adequaciesByCountry, country, CountryAdequacy.ValueType.GENERATION, p);
            }
        });

        // net position computation by country
        borderBasedCountryAreas.forEach((country, borderBasedCountryArea) -> {
            double netPosition = borderBasedCountryArea.getNetPosition();
            fillCountryAdequacy(adequaciesByCountry, country, CountryAdequacy.ValueType.NET_POSITION, netPosition);
        });

        // reverse sign of generation and losses computation by country : P - C - net position
        adequaciesByCountry.forEach((key, value) -> {
            value.setGeneration(-value.getGeneration());
            value.setLosses(value.getGeneration() - value.getLoad() - value.getNetPosition());
        });

        return adequaciesByCountry.entrySet().stream()
            .map(entry -> CountryAdequacy.builder()
                .country(entry.getKey())
                .load(entry.getValue().getLoad())
                .generation(entry.getValue().getGeneration())
                .losses(entry.getValue().getLosses())
                .netPosition(entry.getValue().getNetPosition())
                .build())
            .collect(Collectors.toList());
    }

    private void fillExchange(Map<String, List<Exchange>> result, String country, String otherCountry, double exchange) {
        List<Exchange> exchangesCountrytoOtherCountries = result.computeIfAbsent(country, k -> new ArrayList<>());

        OptionalInt indexOfOtherCountry = IntStream.range(0, exchangesCountrytoOtherCountries.size())
            .filter(i -> exchangesCountrytoOtherCountries.get(i).getCountry().equals(otherCountry))
            .findFirst();
        if (indexOfOtherCountry.isEmpty()) {
            exchangesCountrytoOtherCountries.add(new Exchange(null, otherCountry, exchange));
        }
    }

    private Pair<Country, Double> getCountryAndActivePowerFromTerminalInMainComponent(Terminal terminal) {
        Country country;
        double p = Double.NaN;
        if (terminal != null && terminal.isConnected() && terminal.getBusView().getBus() != null && terminal.getBusView().getBus().isInMainConnectedComponent()) {
            Optional<Substation> substation = terminal.getVoltageLevel().getSubstation();
            country = substation.flatMap(Substation::getCountry).orElse(null);
            p = zeroIfNan(terminal.getP());
        } else {
            country = null;
        }
        return Pair.of(country, p);
    }

    private Map<String, List<Exchange>> calculateExchanges(Network network, Map<Country, BorderBasedCountryArea> borderBasedCountryAreas) {
        Map<String, List<Exchange>> result = new HashMap<>();

        Stream.concat(network.getBranchStream(), network.getHvdcLineStream()).forEach(identifiable -> {
            BranchInfos branchInfos = getBranchInfos(identifiable);
            Country country1 = branchInfos.country1;
            Country country2 = branchInfos.country2;

            if (country1 != null && country2 != null && !country1.name().equals(country2.name())) {
                BorderBasedCountryArea borderBasedCountry1 = borderBasedCountryAreas.get(country1);
                BorderBasedCountryArea borderBasedCountry2 = borderBasedCountryAreas.get(country2);

                if (borderBasedCountry1 != null && borderBasedCountry2 != null) {
                    double exchange1to2 = borderBasedCountry1.getLeavingFlowToCountry(borderBasedCountry2);
                    double exchange2to1 = borderBasedCountry2.getLeavingFlowToCountry(borderBasedCountry1);
                    fillExchange(result, country1.name(), country2.name(), exchange1to2);
                    fillExchange(result, country2.name(), country1.name(), exchange2to1);
                }
            }
        });

        return result;
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

        Optional<CurrentLimits> currentLimits = branch.getCurrentLimits(TwoSides.valueOf(limitViolationInfos.getSide()));
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

    /*
     * Spring Cloud Stream does not allow customizing each consumer within a single listener
     * container (i.e. when concurrency = N)
     *
     * Since we need to customize each consumer individually, we simulate "concurrency = N"
     * by creating N listener containers, each with concurrency = 1.
     *
     * This requires defining one Consumer bean per container, which explains
     * the duplicated methods below.
     */
    @Bean
    public Consumer<Message<String>> consumeRun1() {
        return super.consumeRun();
    }

    @Bean
    public Consumer<Message<String>> consumeRun2() {
        return super.consumeRun();
    }

    @Bean
    public Consumer<Message<String>> consumeRun3() {
        return super.consumeRun();
    }

    @Bean
    public Consumer<Message<String>> consumeRun4() {
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
