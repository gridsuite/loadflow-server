/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.loadflow.LoadFlowProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.loadflow.server.dto.*;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.repositories.LimitViolationRepository;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.gridsuite.loadflow.server.computation.service.AbstractComputationService;
import org.gridsuite.loadflow.server.computation.service.NotificationService;
import org.gridsuite.loadflow.server.computation.service.UuidGeneratorService;
import org.gridsuite.loadflow.server.service.parameters.LoadFlowParametersService;
import org.gridsuite.loadflow.server.utils.LoadflowException;
import org.gridsuite.loadflow.server.utils.SpecificationBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final LimitViolationRepository limitViolationRepository;

    public LoadFlowService(NotificationService notificationService,
                           LoadFlowResultRepository resultRepository,
                           ObjectMapper objectMapper,
                           UuidGeneratorService uuidGeneratorService,
                           LoadFlowParametersService parametersService,
                           LimitViolationRepository limitViolationRepository,
                           @Value("${loadflow.default-provider}") String defaultProvider) {
        super(notificationService, resultRepository, objectMapper, uuidGeneratorService, defaultProvider);
        this.parametersService = parametersService;
        this.limitViolationRepository = limitViolationRepository;
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

    public LoadFlowResult getResult(UUID resultUuid, String stringFilters, Sort sort) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Objects.requireNonNull(resultUuid);
        LoadFlowResult loadFlowResult;
        LoadFlowResultEntity loadFlowResultEntity = getResultRepository().findResults(resultUuid).orElse(null);
        if (loadFlowResultEntity == null) {
            return null;
        }
        List<ComponentResultEntity> componentResults = getResultRepository().findComponentResults(resultUuid, fromStringFiltersToDTO(stringFilters), sort);
        loadFlowResultEntity.setComponentResults(componentResults);
        loadFlowResult = fromEntity(loadFlowResultEntity);
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

    public void assertResultExists(UUID resultUuid) {
        if (!limitViolationRepository.existsLimitViolationEntitiesByLoadFlowResultResultUuid(resultUuid)) {
            throw new LoadflowException(LoadflowException.Type.RESULT_NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public List<LimitViolationInfos> getLimitViolationsInfos(UUID resultUuid, String stringFilters, Sort sort) {
        assertResultExists(resultUuid);
        List<LimitViolationEntity> limitViolationResult = findLimitViolations(resultUuid, fromStringFiltersToDTO(stringFilters), sort);
        return limitViolationResult.stream().map(LimitViolationInfos::toLimitViolationInfos).collect(Collectors.toList());
    }

    public List<LimitViolationEntity> findLimitViolations(UUID resultUuid, List<ResourceFilter> resourceFilters, Sort sort) {
        Objects.requireNonNull(resultUuid);
        return findLimitViolationsEntities(resultUuid, resourceFilters, sort);
    }

    private List<LimitViolationEntity> findLimitViolationsEntities(UUID limitViolationUuid, List<ResourceFilter> resourceFilters, Sort sort) {
        Specification<LimitViolationEntity> specification = SpecificationBuilder.buildLimitViolationsSpecifications(limitViolationUuid, resourceFilters);
        return limitViolationRepository.findAll(specification, sort);
    }

    public List<ResourceFilter> fromStringFiltersToDTO(String stringFilters) {
        if (StringUtils.isEmpty(stringFilters)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stringFilters, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new LoadflowException(LoadflowException.Type.INVALID_FILTER_FORMAT);
        }
    }
}
