/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowSpecificParameterInfos;

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

    @Column(name = "voltageInitMode")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.VoltageInitMode voltageInitMode;

    @Column(name = "transformerVoltageControlOn", columnDefinition = "boolean default false")
    private boolean transformerVoltageControlOn;

    @Column(name = "useReactiveLimits", columnDefinition = "boolean default true")
    private boolean useReactiveLimits;

    @Column(name = "phaseShifterRegulationOn", columnDefinition = "boolean default false")
    private boolean phaseShifterRegulationOn;

    @Column(name = "twtSplitShuntAdmittance", columnDefinition = "boolean default false")
    private boolean twtSplitShuntAdmittance;

    @Column(name = "shuntCompensatorVoltageControlOn", columnDefinition = "boolean default false")
    private boolean shuntCompensatorVoltageControlOn;

    @Column(name = "readSlackBus", columnDefinition = "boolean default true")
    private boolean readSlackBus;

    @Column(name = "writeSlackBus", columnDefinition = "boolean default false")
    private boolean writeSlackBus;

    @Column(name = "dc", columnDefinition = "boolean default false")
    private boolean dc;

    @Column(name = "distributedSlack", columnDefinition = "boolean default true")
    private boolean distributedSlack;

    @Column(name = "balanceType")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.BalanceType balanceType;

    @Column(name = "dcUseTransformerRatio", columnDefinition = "boolean default true")
    private boolean dcUseTransformerRatio;

    @Column(name = "countriesToBalance")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "loadFlowParametersEntity_countriesToBalance_fk1"),
            indexes = {@Index(name = "loadFlowParametersEntity_countriesToBalance_idx1",
                    columnList = "load_flow_parameters_entity_id")})
    private Set<String> countriesToBalance;

    @Column(name = "connectedComponentMode")
    @Enumerated(EnumType.STRING)
    private LoadFlowParameters.ConnectedComponentMode connectedComponentMode;

    @Column(name = "hvdcAcEmulation", columnDefinition = "boolean default true")
    private boolean hvdcAcEmulation;

    @Column(name = "dcPowerFactor", columnDefinition = "double default 1.0")
    private double dcPowerFactor;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "load_flow_parameters_id")
    private List<LoadFlowSpecificParameterEntity> specificParameters;

    public LoadFlowParametersEntity(LoadFlowParametersInfos loadFlowParametersInfos) {
        assignAttributes(loadFlowParametersInfos);
    }

    public void update(LoadFlowParametersInfos loadFlowParametersInfos) {
        assignAttributes(loadFlowParametersInfos);
    }

    public void assignAttributes(LoadFlowParametersInfos loadFlowParametersInfos) {
        LoadFlowParameters allCommonValues;
        List<LoadFlowSpecificParameterInfos> allSpecificValues = new ArrayList<>(List.of());
        if (loadFlowParametersInfos == null) {
            allCommonValues = LoadFlowParameters.load();
        } else {
            allCommonValues = loadFlowParametersInfos.getCommonParameters();
            if (loadFlowParametersInfos.getSpecificParametersPerProvider() != null) {
                loadFlowParametersInfos.getSpecificParametersPerProvider().forEach((provider, paramsMap) -> {
                    if (paramsMap != null) {
                        paramsMap.forEach((paramName, paramValue) -> {
                                if (paramValue != null) {
                                    allSpecificValues.add(LoadFlowSpecificParameterInfos.builder()
                                            .provider(provider)
                                            .value(Objects.toString(paramValue))
                                            .name(paramName)
                                            .build());
                                }
                            }
                        );
                    }
                });
            }
        }
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
        List<LoadFlowSpecificParameterEntity> allSpecificValuesEntities = LoadFlowSpecificParameterEntity.toLoadFlowSpecificParameters(allSpecificValues);
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

    public LoadFlowParametersInfos toLoadFlowParametersInfos() {
        return LoadFlowParametersInfos.builder()
                .uuid(id)
                .commonParameters(toLoadFlowParameters())
                .specificParametersPerProvider(specificParameters.stream()
                        .collect(Collectors.groupingBy(LoadFlowSpecificParameterEntity::getProvider,
                                Collectors.toMap(LoadFlowSpecificParameterEntity::getName,
                                        LoadFlowSpecificParameterEntity::getValue))))
                .build();
    }

    public LoadFlowParametersValues toLoadFlowParametersValues(String provider) {
        return LoadFlowParametersValues.builder()
                .commonParameters(toLoadFlowParameters())
                .specificParameters(specificParameters.stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase(provider))
                        .collect(Collectors.toMap(LoadFlowSpecificParameterEntity::getName,
                                LoadFlowSpecificParameterEntity::getValue)))
                .build();
    }
}
