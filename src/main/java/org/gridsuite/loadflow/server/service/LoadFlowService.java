/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.computation.service.AbstractComputationService;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.computation.service.UuidGeneratorService;
import org.gridsuite.loadflow.server.dto.*;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.SlackBusResultEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class LoadFlowService extends AbstractComputationService<LoadFlowRunContext, LoadFlowResultService, LoadFlowStatus> {
    protected static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowService.class);

    public static final String COMPUTATION_TYPE = "loadflow";

    private final LoadFlowParametersService parametersService;

    public LoadFlowService(NotificationService notificationService,
                           LoadFlowResultService resultService,
                           ObjectMapper objectMapper,
                           UuidGeneratorService uuidGeneratorService,
                           LoadFlowParametersService parametersService,
                           @Value("${loadflow.default-provider}") String defaultProvider) {
        super(notificationService, resultService, objectMapper, uuidGeneratorService, defaultProvider);
        this.parametersService = parametersService;
    }

    public UUID createRunningStatus() {
        UUID randomUuid = uuidGeneratorService.generate();
        setStatus(List.of(randomUuid), LoadFlowStatus.RUNNING);
        return randomUuid;
    }

    @Override
    public List<String> getProviders() {
        return LoadFlowProvider.findAll().stream()
                .map(LoadFlowProvider::getName)
                .toList();
    }

    public static Map<String, List<Parameter>> getSpecificLoadFlowParameters(String providerName) {
        return LoadFlowProvider.findAll().stream()
                .filter(provider -> providerName == null || provider.getName().equals(providerName))
                .map(provider -> {
                    List<Parameter> params = provider.getSpecificParameters(PlatformConfig.defaultConfig()).stream()
                            .filter(p -> p.getScope() == ParameterScope.FUNCTIONAL)
                            .toList();
                    return Pair.of(provider.getName(), params);
                }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    @Override
    @Transactional
    public UUID runAndSaveResult(LoadFlowRunContext loadFlowRunContext) {
        LoadFlowParametersValues params = parametersService.getParametersValues(loadFlowRunContext.getParametersUuid());
        params.getCommonParameters().setTransformerVoltageControlOn(loadFlowRunContext.isWithRatioTapChangers());
        // set provider and parameters
        loadFlowRunContext.setParameters(params);
        loadFlowRunContext.setProvider(params.getProvider() != null ? params.getProvider() : getDefaultProvider());
        UUID resultUuid = loadFlowRunContext.getResultUuid();

        // update status to running status
        setStatus(List.of(resultUuid), LoadFlowStatus.RUNNING);
        notificationService.sendRunMessage(new LoadFlowResultContext(resultUuid, loadFlowRunContext).toMessage(objectMapper));
        return resultUuid;
    }

    public static LoadFlowStatus computeLoadFlowStatus(com.powsybl.loadflow.LoadFlowResult result) {
        return result.getComponentResults().stream()
                .filter(cr -> cr.getConnectedComponentNum() == 0 && cr.getSynchronousComponentNum() == 0
                        && cr.getStatus() == com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status.CONVERGED)
                .toList().isEmpty() ? LoadFlowStatus.DIVERGED : LoadFlowStatus.CONVERGED;
    }

    public List<Status> getComputationStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultService.findComputingStatus(resultUuid);
    }

    static ComponentResult fromEntity(ComponentResultEntity componentResultEntity, List<SlackBusResultEntity> slackBusResultEntities, boolean hasChildFilter) {
        return ComponentResult.builder()
                .componentResultUuid(componentResultEntity.getComponentResultUuid())
                .connectedComponentNum(componentResultEntity.getConnectedComponentNum())
                .synchronousComponentNum(componentResultEntity.getSynchronousComponentNum())
                .status(componentResultEntity.getStatus())
                .iterationCount(componentResultEntity.getIterationCount())
                .slackBusResults(getSlackBusResult(hasChildFilter, slackBusResultEntities, componentResultEntity))
                .distributedActivePower(componentResultEntity.getDistributedActivePower())
                .build();
    }

    private static List<SlackBusResult> getSlackBusResult(boolean hasChildFilter, List <SlackBusResultEntity> slackBusResultEntities, ComponentResultEntity componentResultEntity) {
        List <SlackBusResultEntity> slackBusResults = new ArrayList<>();
        if (!hasChildFilter) {
            slackBusResults.addAll(componentResultEntity.getSlackBusResults());
        } else {
            // map the componentResultUuid to the associated slackBusResult entities
            Map<UUID, List<SlackBusResultEntity>> map = slackBusResultEntities.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(slackBusResultEntity -> slackBusResultEntity.getComponentResult().getComponentResultUuid()));
            if (map.isEmpty()) {
                return List.of();
            }
            slackBusResults.addAll(map.get(componentResultEntity.getComponentResultUuid()));
        }

        return slackBusResults.stream().map(slackBusResultEntity -> new SlackBusResult(slackBusResultEntity.getId(), slackBusResultEntity.getActivePowerMismatch())).toList();
    }
}
