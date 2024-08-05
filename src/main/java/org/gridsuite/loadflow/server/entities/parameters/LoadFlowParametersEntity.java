/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.entities.parameters;

import com.powsybl.iidm.network.Country;
import com.powsybl.loadflow.LoadFlowParameters;
import lombok.*;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "loadFlowParameters")
public class LoadFlowParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "provider")
    private String provider;

    @Column(name = "voltageInitMode")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.VoltageInitMode voltageInitMode;

    @Column(name = "transformerVoltageControlOn", columnDefinition = "boolean default false", nullable = false)
    private boolean transformerVoltageControlOn = false;

    @Column(name = "useReactiveLimits", columnDefinition = "boolean default true", nullable = false)
    private boolean useReactiveLimits = true;

    @Column(name = "phaseShifterRegulationOn", columnDefinition = "boolean default false", nullable = false)
    private boolean phaseShifterRegulationOn = false;

    @Column(name = "twtSplitShuntAdmittance", columnDefinition = "boolean default false", nullable = false)
    private boolean twtSplitShuntAdmittance = false;

    @Column(name = "shuntCompensatorVoltageControlOn", columnDefinition = "boolean default false", nullable = false)
    private boolean shuntCompensatorVoltageControlOn = false;

    @Column(name = "readSlackBus", columnDefinition = "boolean default true", nullable = false)
    private boolean readSlackBus = true;

    @Column(name = "writeSlackBus", columnDefinition = "boolean default false", nullable = false)
    private boolean writeSlackBus = false;

    @Column(name = "dc", columnDefinition = "boolean default false", nullable = false)
    private boolean dc = false;

    @Column(name = "distributedSlack", columnDefinition = "boolean default true", nullable = false)
    private boolean distributedSlack = true;

    @Column(name = "balanceType")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.BalanceType balanceType;

    @Column(name = "dcUseTransformerRatio", columnDefinition = "boolean default true", nullable = false)
    private boolean dcUseTransformerRatio = true;

    @Column(name = "countriesToBalance")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "loadFlowParametersEntity_countriesToBalance_fk1"),
            indexes = {@Index(name = "loadFlowParametersEntity_countriesToBalance_idx1",
                    columnList = "load_flow_parameters_entity_id")})
    private Set<String> countriesToBalance;

    @Column(name = "connectedComponentMode")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.ConnectedComponentMode connectedComponentMode;

    @Column(name = "hvdcAcEmulation", columnDefinition = "boolean default true", nullable = false)
    private boolean hvdcAcEmulation = true;

    @Column(name = "dcPowerFactor", columnDefinition = "double default 1.0", nullable = false)
    private double dcPowerFactor = 1.0;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "load_flow_parameters_id", foreignKey = @ForeignKey(name = "loadFlowParametersEntity_specificParameters_fk"))
    private List<LoadFlowSpecificParameterEntity> specificParameters;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "load_flow_parameters_id", foreignKey = @ForeignKey(name = "loadFlowParametersEntity_limitReductions_fk"))
    @OrderColumn(name = "index")
    private List<LimitReductionEntity> limitReductions;

    public LoadFlowParametersEntity(LoadFlowParametersInfos loadFlowParametersInfos) {
        assignAttributes(loadFlowParametersInfos);
    }

    public void update(LoadFlowParametersInfos loadFlowParametersInfos) {
        assignAttributes(loadFlowParametersInfos);
    }

    public void assignAttributes(LoadFlowParametersInfos loadFlowParametersInfos) {
        LoadFlowParameters allCommonValues;
        List<LoadFlowSpecificParameterEntity> allSpecificValuesEntities = new ArrayList<>(List.of());
        List<LimitReductionEntity> limitReductionEntities = new ArrayList<>(List.of());

        if (loadFlowParametersInfos == null) {
            allCommonValues = LoadFlowParameters.load();
        } else {
            limitReductionEntities.addAll(loadFlowParametersInfos.getLimitReductionsValues().stream().map(LimitReductionEntity::new).toList());
            allCommonValues = loadFlowParametersInfos.getCommonParameters();
            if (loadFlowParametersInfos.getSpecificParametersPerProvider() != null) {
                loadFlowParametersInfos.getSpecificParametersPerProvider().forEach((p, paramsMap) -> {
                    if (paramsMap != null) {
                        paramsMap.forEach((paramName, paramValue) -> {
                            if (paramValue != null) {
                                allSpecificValuesEntities.add(new LoadFlowSpecificParameterEntity(
                                        null,
                                        p,
                                        paramName,
                                        paramValue));
                            }
                        });
                    }
                });
            }
            provider = loadFlowParametersInfos.getProvider();
        }
        assignCommonValues(allCommonValues);
        assignSpecificValues(allSpecificValuesEntities);
        assignLimitReduction(limitReductionEntities);
    }

    private void assignLimitReduction(List<LimitReductionEntity> limitReductionEntities) {
        if (limitReductions == null) {
            limitReductions = limitReductionEntities;
        } else {
            limitReductions.clear();
            if (!limitReductionEntities.isEmpty()) {
                limitReductions.addAll(limitReductionEntities);
            }
        }
    }

    private void assignCommonValues(LoadFlowParameters allCommonValues) {
        voltageInitMode = allCommonValues.getVoltageInitMode();
        transformerVoltageControlOn = allCommonValues.isTransformerVoltageControlOn();
        useReactiveLimits = allCommonValues.isUseReactiveLimits();
        phaseShifterRegulationOn = allCommonValues.isPhaseShifterRegulationOn();
        twtSplitShuntAdmittance = allCommonValues.isTwtSplitShuntAdmittance();
        shuntCompensatorVoltageControlOn = allCommonValues.isShuntCompensatorVoltageControlOn();
        readSlackBus = allCommonValues.isReadSlackBus();
        writeSlackBus = allCommonValues.isWriteSlackBus();
        dc = allCommonValues.isDc();
        distributedSlack = allCommonValues.isDistributedSlack();
        balanceType = allCommonValues.getBalanceType();
        dcUseTransformerRatio = allCommonValues.isDcUseTransformerRatio();
        countriesToBalance = allCommonValues.getCountriesToBalance().stream().map(Country::toString).collect(Collectors.toSet());
        connectedComponentMode = allCommonValues.getConnectedComponentMode();
        hvdcAcEmulation = allCommonValues.isHvdcAcEmulation();
        dcPowerFactor = allCommonValues.getDcPowerFactor();
    }

    private void assignSpecificValues(List<LoadFlowSpecificParameterEntity> allSpecificValuesEntities) {
        if (specificParameters == null) {
            specificParameters = allSpecificValuesEntities;
        } else {
            specificParameters.clear();
            if (!allSpecificValuesEntities.isEmpty()) {
                specificParameters.addAll(allSpecificValuesEntities);
            }
        }
    }

    public LoadFlowParameters toLoadFlowParameters() {
        return LoadFlowParameters.load()
                .setVoltageInitMode(this.getVoltageInitMode())
                .setTransformerVoltageControlOn(this.isTransformerVoltageControlOn())
                .setUseReactiveLimits(this.isUseReactiveLimits())
                .setPhaseShifterRegulationOn(this.isPhaseShifterRegulationOn())
                .setTwtSplitShuntAdmittance(this.isTwtSplitShuntAdmittance())
                .setShuntCompensatorVoltageControlOn(this.isShuntCompensatorVoltageControlOn())
                .setReadSlackBus(this.isReadSlackBus())
                .setWriteSlackBus(this.isWriteSlackBus())
                .setDc(this.isDc())
                .setDistributedSlack(this.isDistributedSlack())
                .setBalanceType(this.getBalanceType())
                .setDcUseTransformerRatio(this.isDcUseTransformerRatio())
                .setCountriesToBalance(this.getCountriesToBalance().stream().map(Country::valueOf).collect(Collectors.toSet()))
                .setConnectedComponentMode(this.getConnectedComponentMode())
                .setHvdcAcEmulation(this.isHvdcAcEmulation())
                .setDcPowerFactor(this.getDcPowerFactor());
    }

    public List<List<Double>> toLimitReductionsValues() {
        return this.limitReductions.stream().map(LimitReductionEntity::getReductions).map(ArrayList::new).collect(Collectors.toList());
    }

    public LoadFlowParametersEntity copy(LoadFlowParametersInfos loadFlowParametersInfos) {
        return loadFlowParametersInfos.toEntity();
    }
}
