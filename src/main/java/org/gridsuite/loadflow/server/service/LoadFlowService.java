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
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.security.LimitViolationType;
import com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.gridsuite.loadflow.server.dto.*;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.entities.SlackBusResultEntity;
import org.gridsuite.loadflow.server.repositories.LimitViolationRepository;
import org.gridsuite.loadflow.server.repositories.LoadFlowResultService;
import org.gridsuite.loadflow.server.computation.service.AbstractComputationService;
import org.gridsuite.loadflow.server.computation.service.NotificationService;
import org.gridsuite.loadflow.server.computation.service.UuidGeneratorService;
import org.gridsuite.loadflow.server.utils.FilterUtils;
import org.gridsuite.loadflow.server.utils.LoadflowException;
import org.gridsuite.loadflow.server.utils.SpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
public class LoadFlowService extends AbstractComputationService<LoadFlowRunContext, LoadFlowResultService, LoadFlowStatus> {
    protected static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowService.class);

    public static final String COMPUTATION_TYPE = "loadflow";

    private final LoadFlowParametersService parametersService;
    private final FilterService filterService;
    private final LimitViolationRepository limitViolationRepository;

    public LoadFlowService(NotificationService notificationService,
                           LoadFlowResultService resultService,
                           ObjectMapper objectMapper,
                           UuidGeneratorService uuidGeneratorService,
                           LoadFlowParametersService parametersService,
                           FilterService filterService,
                           LimitViolationRepository limitViolationRepository,
                           @Value("${loadflow.default-provider}") String defaultProvider) {
        super(notificationService, resultService, objectMapper, uuidGeneratorService, defaultProvider);
        this.parametersService = parametersService;
        this.filterService = filterService;
        this.limitViolationRepository = limitViolationRepository;
    }

    @Override
    public List<String> getProviders() {
        return LoadFlowProvider.findAll().stream()
                .map(LoadFlowProvider::getName)
                .toList();
    }

    public static Map<String, List<Parameter>> getSpecificLoadFlowParameters(String providerName) {
        Map<String, List<Parameter>> powsyblSpecificLFParameters = LoadFlowProvider.findAll().stream()
                .filter(provider -> providerName == null || provider.getName().equals(providerName))
                .map(provider -> {
                    List<Parameter> params = provider.getSpecificParameters().stream()
                            .filter(p -> p.getScope() == ParameterScope.FUNCTIONAL)
                            .toList();
                    return Pair.of(provider.getName(), params);
                }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        //FIXME: We need to override powsybl default values as up to now we can't override those values through PlatformConfig. To be removed when it's fixed.
        powsyblSpecificLFParameters.put("OpenLoadFlow", changeDefaultValue(powsyblSpecificLFParameters, "OpenLoadFlow", "writeReferenceTerminals", false));
        powsyblSpecificLFParameters.put("DynaFlow", changeDefaultValue(powsyblSpecificLFParameters, "DynaFlow", "mergeLoads", false));

        return powsyblSpecificLFParameters;
    }

    public static List<Parameter> changeDefaultValue(Map<String, List<Parameter>> specificParameters, String providerName, String parameterName, Object defaultValue) {
        List<Parameter> providerParams = new ArrayList<>(specificParameters.get(providerName));
        providerParams.stream().filter(parameter -> parameterName.equals(parameter.getName())).findAny().ifPresent(parameterToOverride ->
        {
            providerParams.remove(parameterToOverride);
            providerParams.add(new Parameter(parameterToOverride.getName(),
                    parameterToOverride.getType(),
                    parameterToOverride.getDescription(),
                    defaultValue,
                    parameterToOverride.getPossibleValues(),
                    parameterToOverride.getScope()
            ));
        });
        return providerParams;
    }

    @Override
    public UUID runAndSaveResult(LoadFlowRunContext loadFlowRunContext) {
        LoadFlowParametersValues params = parametersService.getParametersValues(loadFlowRunContext.getParametersUuid());
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
        LoadFlowResultEntity loadFlowResultEntity = resultService.findResults(resultUuid).orElse(null);
        if (loadFlowResultEntity == null) {
            return null;
        }
        List<ResourceFilter> resourceFilters = fromStringFiltersToDTO(stringFilters);
        List<ComponentResultEntity> componentResults = resultService.findComponentResults(resultUuid, resourceFilters, sort);
        boolean hasChildFilter = resourceFilters.stream().anyMatch(resourceFilter -> !SpecificationBuilder.isParentFilter(resourceFilter));
        List<SlackBusResultEntity> slackBusResultEntities = new ArrayList<>();
        if (hasChildFilter) {
            slackBusResultEntities.addAll(resultService.findSlackBusResults(componentResults, resourceFilters));
        }
        loadFlowResultEntity.setComponentResults(componentResults);
        loadFlowResult = fromEntity(loadFlowResultEntity, slackBusResultEntities, hasChildFilter);
        LOGGER.info("Get LoadFlow Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
        return loadFlowResult;
    }

    public static LoadFlowStatus computeLoadFlowStatus(com.powsybl.loadflow.LoadFlowResult result) {
        return result.getComponentResults().stream()
                .filter(cr -> cr.getConnectedComponentNum() == 0 && cr.getSynchronousComponentNum() == 0
                        && cr.getStatus() == com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status.CONVERGED)
                .toList().isEmpty() ? LoadFlowStatus.DIVERGED : LoadFlowStatus.CONVERGED;
    }

    @Transactional(readOnly = true)
    public List<LimitViolationInfos> getLimitViolationsInfos(UUID resultUuid, String stringFilters, String stringGlobalFilters, Sort sort, UUID networkUuid, String variantId) {
        if (!limitViolationRepository.existsLimitViolationEntitiesByLoadFlowResultResultUuid(resultUuid)) {
            return List.of();
        }

        // get resource filters and global filters
        List<ResourceFilter> resourceFilters = FilterUtils.fromStringFiltersToDTO(stringFilters, objectMapper);
        GlobalFilter globalFilter = FilterUtils.fromStringGlobalFiltersToDTO(stringGlobalFilters, objectMapper);

        if (globalFilter != null) {
            List<ResourceFilter> resourceGlobalFilters = filterService.getResourceFilters(networkUuid, variantId, globalFilter);
            if (!resourceGlobalFilters.isEmpty()) {
                resourceFilters.addAll(resourceGlobalFilters);
            } else {
                return List.of();
            }
        }
        List<LimitViolationEntity> limitViolationResult = findLimitViolations(resultUuid, resourceFilters, sort);
        return limitViolationResult.stream().map(LimitViolationInfos::toLimitViolationInfos).toList();
    }

    public List<LimitViolationType> getLimitTypes(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return limitViolationRepository.findLimitTypes(resultUuid);
    }

    public List<TwoSides> getBranchSides(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return limitViolationRepository.findBranchSides(resultUuid);
    }

    public List<Status> getComputationStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultService.findComputingStatus(resultUuid);
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
