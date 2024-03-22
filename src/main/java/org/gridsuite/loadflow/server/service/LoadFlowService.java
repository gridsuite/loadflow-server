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
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.network.store.client.NetworkStoreService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.FilterServiceUtils;
import org.gridsuite.loadflow.server.computation.service.AbstractComputationService;
import org.gridsuite.loadflow.server.computation.service.NotificationService;
import org.gridsuite.loadflow.server.computation.service.UuidGeneratorService;
import org.gridsuite.loadflow.server.dto.*;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.entities.SlackBusResultEntity;
import org.gridsuite.loadflow.server.repositories.LimitViolationRepository;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultRepository;
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
    private final NetworkStoreService networkStoreService;
    private final LimitViolationRepository limitViolationRepository;

    public LoadFlowService(NotificationService notificationService,
                           LoadFlowResultRepository resultRepository,
                           ObjectMapper objectMapper,
                           UuidGeneratorService uuidGeneratorService,
                           LoadFlowParametersService parametersService,
                           FilterService filterService,
                           NetworkStoreService networkStoreService,
                           LimitViolationRepository limitViolationRepository,
                           @Value("${loadflow.default-provider}") String defaultProvider) {
        super(notificationService, resultRepository, objectMapper, uuidGeneratorService, defaultProvider);
        this.parametersService = parametersService;
        this.filterService = filterService;
        this.networkStoreService = networkStoreService;
        this.limitViolationRepository = limitViolationRepository;
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

    private static List<SlackBusResult> getSlackBusResult(boolean hasChildFilter, List<SlackBusResultEntity> slackBusResultEntities, ComponentResultEntity componentResultEntity) {
        List<SlackBusResultEntity> slackBusResults = new ArrayList<>();
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

    @Transactional(readOnly = true)
    public List<LimitViolationInfos> getLimitViolationsInfos(UUID resultUuid, String stringFilters, String stringGlobalFilters, Sort sort, UUID networkUuid) {
        if (!limitViolationRepository.existsLimitViolationEntitiesByLoadFlowResultResultUuid(resultUuid)) {
            return List.of();
        }

        List<ResourceFilter> resourceFilters = new ArrayList<>();
        resourceFilters.addAll(fromStringFiltersToDTO(stringFilters));
        if (stringGlobalFilters != null) {
            List<String> subjectIds = createFilterFromGlobalFilter(stringGlobalFilters, networkUuid);

            if (!subjectIds.isEmpty()) {
                resourceFilters.add(new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.IN, subjectIds, ResourceFilter.Column.SUBJECT_ID));
            }
        }

        List<LimitViolationEntity> limitViolationResult = findLimitViolations(resultUuid, resourceFilters, sort);
        return limitViolationResult.stream().map(LimitViolationInfos::toLimitViolationInfos).collect(Collectors.toList());
    }

    public List<String> createFilterFromGlobalFilter(String stringGlobalFilters, UUID networkUuid) {
        GlobalFilter globalFilter = fromStringGlobalFiltersToDTO(stringGlobalFilters);
        List<String> subjectIds = new ArrayList<>();
        List<EquipmentType> equipmentTypes = new ArrayList<>();
        if (globalFilter.getLimitViolationsType().equals("CURRENT")) {
            equipmentTypes.add(EquipmentType.LINE);
            equipmentTypes.add(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        } else {
            equipmentTypes.add(EquipmentType.VOLTAGE_LEVEL);
        }
        if (globalFilter.getCountryCode() != null || globalFilter.getNominalV() != null) {
            Network network = networkStoreService.getNetwork(networkUuid);

            for (EquipmentType equipmentType : equipmentTypes) {
                ExpertFilter expertFilter = filterService.buildExpertFilter(globalFilter, equipmentType);
                subjectIds.addAll(FilterServiceUtils.getIdentifiableAttributes(expertFilter, network, null).stream().map(e -> e.getId()).collect(Collectors.toList()));
            }
        }
        return subjectIds;
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

    public GlobalFilter fromStringGlobalFiltersToDTO(String stringFilters) {
        if (StringUtils.isEmpty(stringFilters)) {
            return null;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(stringFilters, GlobalFilter.class);
        } catch (JsonProcessingException e) {
            throw new LoadflowException(LoadflowException.Type.INVALID_FILTER_FORMAT);
        }
    }
}
