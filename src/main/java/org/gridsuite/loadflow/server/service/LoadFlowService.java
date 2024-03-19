/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.loadflow.LoadFlowProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.loadflow.server.dto.*;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.entities.SlackBusResultEntity;
import org.gridsuite.loadflow.server.repositories.LimitViolationRepository;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
import org.gridsuite.loadflow.server.computation.service.AbstractComputationService;
import org.gridsuite.loadflow.server.computation.service.NotificationService;
import org.gridsuite.loadflow.server.computation.service.UuidGeneratorService;
import org.gridsuite.loadflow.server.service.filters.FilterCreatorService;
import org.gridsuite.loadflow.server.service.filters.FilterService;
import org.gridsuite.loadflow.server.service.parameters.LoadFlowParametersService;
import org.gridsuite.loadflow.server.utils.LoadflowException;
import org.gridsuite.loadflow.server.utils.SpecificationBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
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
    private final FilterService filterService;
    private final FilterCreatorService filterCreatorService;
    private final LimitViolationRepository limitViolationRepository;

    public LoadFlowService(NotificationService notificationService,
                           LoadFlowResultRepository resultRepository,
                           ObjectMapper objectMapper,
                           UuidGeneratorService uuidGeneratorService,
                           LoadFlowParametersService parametersService,
                           FilterService filterService,
                           FilterCreatorService filterCreatorService,
                           LimitViolationRepository limitViolationRepository,
                           @Value("${loadflow.default-provider}") String defaultProvider) {
        super(notificationService, resultRepository, objectMapper, uuidGeneratorService, defaultProvider);
        this.parametersService = parametersService;
        this.filterService = filterService;
        this.filterCreatorService = filterCreatorService;
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
    public UUID runAndSaveResult(LoadFlowRunContext loadFlowRunContext, UUID parametersUuid) {
        LoadFlowParametersValues params = parametersService.getParametersValues(parametersUuid);
        // set provider and parameters
        loadFlowRunContext.setParameters(params);
        loadFlowRunContext.setProvider(params.provider() != null ? params.provider() : getDefaultProvider());
        UUID resultUuid = uuidGeneratorService.generate();

        // update status to running status
        setStatus(List.of(resultUuid), LoadFlowStatus.RUNNING);
        notificationService.sendRunMessage(new LoadFlowResultContext(resultUuid, loadFlowRunContext).toMessage(objectMapper));
        return resultUuid;
    }

    private static LoadFlowResult fromEntity(LoadFlowResultEntity resultEntity, List<SlackBusResultEntity> slackBusResultEntities, boolean hasChildFilter) {
        return LoadFlowResult.builder()
                .resultUuid(resultEntity.getResultUuid())
                .writeTimeStamp(resultEntity.getWriteTimeStamp())
                .componentResults(resultEntity.getComponentResults().stream().map(result -> LoadFlowService.fromEntity(result, slackBusResultEntities, hasChildFilter)).toList())
                .build();
    }

    private static ComponentResult fromEntity(ComponentResultEntity componentResultEntity, List<SlackBusResultEntity> slackBusResultEntities, boolean hasChildFilter) {
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

    public LoadFlowResult getResult(UUID resultUuid, String stringFilters, Sort sort) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Objects.requireNonNull(resultUuid);
        LoadFlowResult loadFlowResult;
        LoadFlowResultEntity loadFlowResultEntity = getResultRepository().findResults(resultUuid).orElse(null);
        if (loadFlowResultEntity == null) {
            return null;
        }
        List<ResourceFilter> resourceFilters = fromStringFiltersToDTO(stringFilters);
        List<ComponentResultEntity> componentResults = getResultRepository().findComponentResults(resultUuid, resourceFilters, sort);
        boolean hasChildFilter = resourceFilters.stream().anyMatch(resourceFilter -> !SpecificationBuilder.isParentFilter(resourceFilter));
        List<SlackBusResultEntity> slackBusResultEntities = new ArrayList<>();
        if (hasChildFilter) {
            slackBusResultEntities.addAll(getResultRepository().findSlackBusResults(componentResults, resourceFilters));
        }
        loadFlowResultEntity.setComponentResults(componentResults);
        loadFlowResult = fromEntity(loadFlowResultEntity, slackBusResultEntities, hasChildFilter);
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

    @Transactional(readOnly = true)
    public List<LimitViolationInfos> getLimitViolationsInfos(UUID resultUuid, String stringFilters, String stringGlobalFilters, Sort sort, String variantId, UUID networkUuid) {
        if (!limitViolationRepository.existsLimitViolationEntitiesByLoadFlowResultResultUuid(resultUuid)) {
            return List.of();
        }
        List<String> SubjectIdsfromEvaluateFilter = new ArrayList<>();
        List<String> equipmentTypes =List.of("VOLTAGE_LEVEL");
        for (String equipmentType : equipmentTypes) {
            //TODO: use library filter to create expert filter instead of filterCreatorService
            String createdFilter = filterCreatorService.createFilterFromGlobalFilter(fromStringGlobalFiltersToDTO(stringGlobalFilters),equipmentType);
            //TODO: use library filter to evaluate expert filter instead of endpoint
            String resultFromEvaluateFilters = filterService.evaluateFilter(networkUuid,variantId, createdFilter);
            SubjectIdsfromEvaluateFilter.addAll(extractIds(resultFromEvaluateFilters));
        }

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

    public List<GlobalFilter> fromStringGlobalFiltersToDTO(String stringFilters) {
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

    public static List<String> extractIds(String result) {
        List<String> ids = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(result);
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    String id = node.get("id").asText();
                    ids.add(id);
                }
            }
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        return ids;
    }
}
