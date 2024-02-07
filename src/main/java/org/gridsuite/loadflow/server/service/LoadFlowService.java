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
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.loadflow.server.dto.*;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationsEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.repositories.LimitViolationsRepository;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.gridsuite.loadflow.server.service.parameters.LoadFlowParametersService;
import org.gridsuite.loadflow.server.utils.LoadflowException;
import org.gridsuite.loadflow.server.utils.SpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class LoadFlowService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowService.class);

    private static final String PREFIX_SORT_LOADFLOW_RESULT = "componentResults.";
    private static final String PREFIX_SORT_LIMI_VIOLATION_RESULT = "limitViolations.";

    @Value("${loadflow.default-provider}")
    private String defaultProvider;

    private LoadFlowResultRepository resultRepository;

    private ObjectMapper objectMapper;

    NotificationService notificationService;

    private UuidGeneratorService uuidGeneratorService;

    private LoadFlowParametersService parametersService;

    private LimitViolationsRepository limitViolationsRepository;

    public LoadFlowService(NotificationService notificationService, LoadFlowResultRepository resultRepository, ObjectMapper objectMapper, UuidGeneratorService uuidGeneratorService, LoadFlowParametersService parametersService, LimitViolationsRepository limitViolationsRepository) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.resultRepository = Objects.requireNonNull(resultRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.uuidGeneratorService = Objects.requireNonNull(uuidGeneratorService);
        this.parametersService = Objects.requireNonNull(parametersService);
        this.limitViolationsRepository = Objects.requireNonNull(limitViolationsRepository);
    }

    public static List<String> getProviders() {
        return LoadFlowProvider.findAll().stream()
                .map(LoadFlowProvider::getName)
                .collect(Collectors.toList());
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setStatus(List<UUID> resultUuids, LoadFlowStatus status) {
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

    public LoadFlowResult getResult(UUID resultUuid, String stringFilters, Sort sort) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Sort sortModified = addPrefixToSort(PREFIX_SORT_LOADFLOW_RESULT, sort);
        List<LoadFlowResultEntity> result = resultRepository.findResults(resultUuid, fromStringFiltersToDTO(stringFilters), sortModified);
        LoadFlowResult loadFlowResult = result.stream().map(r -> fromEntity(r)).findFirst().orElse(null);
        LOGGER.info("Get LoadFlow Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
        return loadFlowResult;
    }

    public void deleteResult(UUID resultUuid) {
        resultRepository.delete(resultUuid);
    }

    public void deleteResults(List<UUID> resultUuids) {
        if (resultUuids != null && !resultUuids.isEmpty()) {
            resultUuids.forEach(resultRepository::delete);
        } else {
            deleteResults();
        }
    }

    public void deleteResults() {
        resultRepository.deleteAll();
    }

    public LoadFlowStatus getStatus(UUID resultUuid) {
        return resultRepository.findStatus(resultUuid);
    }

    public void stop(UUID resultUuid, String receiver) {
        notificationService.sendCancelMessage(new LoadFlowCancelContext(resultUuid, receiver).toMessage());
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
                .collect(Collectors.toList()).isEmpty() ? LoadFlowStatus.DIVERGED : LoadFlowStatus.CONVERGED;
    }

    public void assertResultExists(UUID resultUuid) {
        if (limitViolationsRepository.findById(resultUuid).isEmpty()) {
            throw new LoadflowException(LoadflowException.Type.RESULT_NOT_FOUND);
        }
    }

    public List<LimitViolationInfos> getLimitViolationsInfos(UUID resultUuid, String stringFilters, Sort sort) {
        assertResultExists(resultUuid);
        Sort sortModified = addPrefixToSort(PREFIX_SORT_LIMI_VIOLATION_RESULT, sort);
        List<LimitViolationsEntity> limitViolationsResult = findLimitViolations(resultUuid, fromStringFiltersToDTO(stringFilters), sortModified);
        return limitViolationsResult.stream()
                .findFirst()
                .map(entity -> entity.getLimitViolations().stream().map(LimitViolationInfos::toLimitViolationInfos).toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<LimitViolationsEntity> findLimitViolations(UUID resultUuid, List<ResourceFilter> resourceFilters, Sort sort) {
        Objects.requireNonNull(resultUuid);
        return findLimitViolationsEntities(resultUuid, resourceFilters, sort);
    }

    private List<LimitViolationsEntity> findLimitViolationsEntities(UUID limitViolationUuid, List<ResourceFilter> resourceFilters, Sort sort) {
        Specification<LimitViolationsEntity> specification = SpecificationBuilder.buildLimitViolationsSpecifications(limitViolationUuid, resourceFilters);
        return limitViolationsRepository.findAll(specification, sort);
    }

    private Sort addPrefixToSort(String prefix, Sort sort) {
        Sort modifiedSort = Sort.unsorted();
        for (Sort.Order originalOrder : sort) {
            String modifiedProperty = prefix + originalOrder.getProperty();
            Sort.Order modifiedOrder = new Sort.Order(Sort.Direction.DESC, modifiedProperty);
            modifiedSort = modifiedSort.and(Sort.by(modifiedOrder));
        }
        return modifiedSort;
    }

    public List<ResourceFilter> fromStringFiltersToDTO(String stringFilters) {
        if (stringFilters == null || stringFilters.isEmpty()) {
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
