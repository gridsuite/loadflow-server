/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.loadflow.LoadFlowProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.loadflow.server.dto.*;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationsEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.gridsuite.loadflow.server.service.computation.AbstractComputationService;
import org.gridsuite.loadflow.server.service.computation.NotificationService;
import org.gridsuite.loadflow.server.service.computation.UuidGeneratorService;
import org.gridsuite.loadflow.server.service.parameters.LoadFlowParametersService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class LoadFlowService extends AbstractComputationService<LoadFlowRunContext> {

    public static final String COMPUTATION_TYPE = "loadflow";

    private final LoadFlowParametersService parametersService;

    public LoadFlowService(NotificationService notificationService,
                           LoadFlowResultRepository resultRepository,
                           ObjectMapper objectMapper,
                           UuidGeneratorService uuidGeneratorService,
                           LoadFlowParametersService parametersService,
                           @Value("${loadflow.default-provider}") String defaultProvider) {
        super(notificationService, resultRepository, objectMapper, uuidGeneratorService, defaultProvider);
        this.parametersService = parametersService;
    }

    private LoadFlowResultRepository getResultRepository() {
        return (LoadFlowResultRepository) resultRepository;
    }

    @Override
    public List<String> getProviders() {
        return LoadFlowProvider.findAll().stream()
                .map(LoadFlowProvider::getName)
                .toList();
    }

    public void setStatus(List<UUID> resultUuids, LoadFlowStatus status) {
        getResultRepository().insertStatus(resultUuids, status);
    }

    public static Map<String, List<Parameter>> getSpecificLoadFlowParameters(String providerName) {
        return LoadFlowProvider.findAll().stream()
                .filter(provider -> providerName == null || provider.getName().equals(providerName))
                .map(provider -> {
                    List<Parameter> params = provider.getSpecificParameters().stream()
                            .filter(p -> p.getScope() == ParameterScope.FUNCTIONAL)
                            .toList();
                    return Pair.of(provider.getName(), params);
                }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    @Override
    public UUID runAndSaveResult(LoadFlowRunContext loadFlowRunContext, String provider, UUID parametersUuid) {
        String providerToUse = provider != null ? provider : getDefaultProvider();
        // set provider and parameters
        loadFlowRunContext.setParameters(parametersService.getParametersValues(parametersUuid, providerToUse).orElse(null));
        loadFlowRunContext.setProvider(providerToUse);
        UUID resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), LoadFlowStatus.RUNNING);
        notificationService.sendRunMessage(new LoadFlowResultContext(resultUuid, loadFlowRunContext).toMessage(objectMapper));
        return resultUuid;
    }

    private static LoadFlowResult fromEntity(LoadFlowResultEntity resultEntity) {
        return LoadFlowResult.builder()
                .resultUuid(resultEntity.getResultUuid())
                .writeTimeStamp(resultEntity.getWriteTimeStamp())
                .componentResults(resultEntity.getComponentResults().stream().map(LoadFlowService::fromEntity).toList())
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
        Optional<LoadFlowResultEntity> result = getResultRepository().findResults(resultUuid);
        LoadFlowResult loadFlowResult = result.map(LoadFlowService::fromEntity).orElse(null);
        LOGGER.info("Get LoadFlow Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
        return loadFlowResult;
    }

    public LoadFlowStatus getStatus(UUID resultUuid) {
        return getResultRepository().findStatus(resultUuid);
    }

    public static String getNonNullHeader(MessageHeaders headers, String name) {
        String header = (String) headers.get(name);
        if (header == null) {
            throw new PowsyblException("Header '" + name + "' not found");
        }
        return header;
    }

    public static LoadFlowStatus computeLoadFlowStatus(com.powsybl.loadflow.LoadFlowResult result) {
        return result.getComponentResults().stream()
                .filter(cr -> cr.getConnectedComponentNum() == 0 && cr.getSynchronousComponentNum() == 0
                        && cr.getStatus() == com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status.CONVERGED)
                .toList().isEmpty() ? LoadFlowStatus.DIVERGED : LoadFlowStatus.CONVERGED;
    }

    public List<LimitViolationInfos> getLimitViolations(UUID resultUuid) {
        Optional<LimitViolationsEntity> limitViolationsEntity = getResultRepository().findLimitViolations(resultUuid);
        LimitViolationsInfos limitViolations = limitViolationsEntity.map(LimitViolationsEntity::toLimitViolationsInfos).orElse(null);
        return limitViolations != null ? limitViolations.getLimitViolations() : Collections.emptyList();
    }
}
