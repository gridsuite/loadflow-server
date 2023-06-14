/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.loadflow.LoadFlowProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.loadflow.server.dto.ComponentResult;
import org.gridsuite.loadflow.server.dto.LoadFlowResult;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class LoadFlowService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowService.class);

    @Value("${loadflow.default-provider}")
    private String defaultProvider;

    private LoadFlowResultRepository resultRepository;

    private ObjectMapper objectMapper;

    @Autowired
    NotificationService notificationService;

    private UuidGeneratorService uuidGeneratorService;

    public LoadFlowService(NotificationService notificationService, LoadFlowResultRepository resultRepository, ObjectMapper objectMapper, UuidGeneratorService uuidGeneratorService) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
    }

    public static List<String> getProviders() {
        return LoadFlowProvider.findAll().stream()
                .map(LoadFlowProvider::getName)
                .collect(Collectors.toList());
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setStatus(List<UUID> resultUuids, String status) {
        resultRepository.insertStatus(resultUuids, status);
    }

    public static Map<String, List<Parameter>> getSpecificLoadFlowParameters(String providerName) {
        return LoadFlowProvider.findAll().stream()
                .filter(provider -> providerName == null || provider.getName().equals(providerName))
                .map(provider -> {
                    List<Parameter> params = provider.getSpecificParameters().stream()
                            .filter(p -> p.getScope() == ParameterScope.FUNCTIONAL)
                            .collect(Collectors.toList());
                    return Pair.of(provider.getName(), params);
                }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    public UUID runAndSaveResult(LoadFlowRunContext runContext) {
        Objects.requireNonNull(runContext);
        UUID resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), LoadFlowStatus.RUNNING.name());
        notificationService.sendRunMessage(new LoadFlowResultContext(resultUuid, runContext).toMessage(objectMapper));
        return resultUuid;
    }

    private static LoadFlowResult fromEntity(LoadFlowResultEntity resultEntity) {
        return LoadFlowResult.builder()
                .resultUuid(resultEntity.getResultUuid())
                .writeTimeStamp(resultEntity.getWriteTimeStamp())
                .componentResults(resultEntity.getComponentResults().stream().map(LoadFlowService::fromEntity).collect(Collectors.toList()))
                .build();
    }

    private static ComponentResult fromEntity(ComponentResultEntity componentResultEntity) {
        return ComponentResult.builder()
                .componentResultUuid(componentResultEntity.getComponentResultUuid())
                .connectedComponentNum(componentResultEntity.getConnectedComponentNum())
                .synchronousComponentNum(componentResultEntity.getSynchronousComponentNum())
                .status(componentResultEntity.getStatus())
                .iterationCount(componentResultEntity.getIterationCount())
                .slackBusId(componentResultEntity.getSlackBusId())
                .slackBusActivePowerMismatch(componentResultEntity.getSlackBusActivePowerMismatch())
                .distributedActivePower(componentResultEntity.getDistributedActivePower())
                .build();
    }

    public LoadFlowResult getResult(UUID resultUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Optional<LoadFlowResultEntity> result = resultRepository.findResults(resultUuid);
        LoadFlowResult loadFlowResult = result.map(r -> fromEntity(r)).orElse(null);
        LOGGER.info("Get LoadFlow Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
        return loadFlowResult;
    }

    public void deleteResult(UUID resultUuid) {
        resultRepository.delete(resultUuid);
    }

    public void deleteResults() {
        resultRepository.deleteAll();
    }

    public String getStatus(UUID resultUuid) {
        return resultRepository.findStatus(resultUuid);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new LoadFlowCancelContext(resultUuid, receiver).toMessage());
    }

}
