/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.security.LimitViolationType;
import lombok.AllArgsConstructor;
import org.gridsuite.computation.ComputationException;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.AbstractComputationResultService;
import org.gridsuite.computation.utils.FilterUtils;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;
import org.gridsuite.loadflow.server.dto.InitialValuesInfos;
import org.gridsuite.loadflow.server.entities.*;
import org.gridsuite.loadflow.server.repositories.ComponentResultRepository;
import org.gridsuite.loadflow.server.repositories.GlobalStatusRepository;
import org.gridsuite.loadflow.server.repositories.LimitViolationRepository;
import org.gridsuite.loadflow.server.repositories.ResultRepository;
import org.gridsuite.loadflow.server.repositories.parameters.SlackBusResultRepository;
import org.gridsuite.loadflow.server.repositories.specifications.ComponentResultSpecificationBuilder;
import org.gridsuite.loadflow.server.repositories.specifications.LimitViolationsSpecificationBuilder;
import org.gridsuite.loadflow.server.repositories.specifications.SlackBusResultSpecificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.gridsuite.computation.utils.FilterUtils.fromStringFiltersToDTO;


/**
 * @author Anis Touri <anis.touri at rte-france.com
 */
@AllArgsConstructor
@Service
public class LoadFlowResultService extends AbstractComputationResultService<LoadFlowStatus> {
    protected static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowResultService.class);

    private GlobalStatusRepository globalStatusRepository;

    private ResultRepository resultRepository;
    private final ComponentResultRepository componentResultRepository;
    private final LimitViolationRepository limitViolationRepository;
    private final SlackBusResultRepository slackBusResultRepository;

    private final LimitViolationsSpecificationBuilder limitViolationsSpecificationBuilder;
    private final ComponentResultSpecificationBuilder componentResultSpecificationBuilder;
    private final SlackBusResultSpecificationBuilder slackBusResultSpecificationBuilder;

    private final ObjectMapper objectMapper;
    private final FilterService filterService;

    private LoadFlowResultEntity toResultEntity(UUID resultUuid, LoadFlowResult result, String solvedValuesInfos, List<LimitViolationInfos> limitViolationInfos) {
        List<ComponentResultEntity> componentResults = result.getComponentResults().stream()
                .map(componentResult -> LoadFlowResultService.toComponentResultEntity(resultUuid, componentResult))
                .toList();
        List<LimitViolationEntity> limitViolations = limitViolationInfos.stream()
                .map(limitViolationInfo -> toLimitViolationsEntity(resultUuid, limitViolationInfo))
                .toList();
        return new LoadFlowResultEntity(resultUuid, Instant.now(), solvedValuesInfos, componentResults, limitViolations);
    }

    private static ComponentResultEntity toComponentResultEntity(UUID resultUuid, LoadFlowResult.ComponentResult componentResult) {
        ComponentResultEntity componentResultEntity = ComponentResultEntity.builder()
                .connectedComponentNum(componentResult.getConnectedComponentNum())
                .synchronousComponentNum(componentResult.getSynchronousComponentNum())
                .status(componentResult.getStatus())
                .iterationCount(componentResult.getIterationCount())
                .distributedActivePower(componentResult.getDistributedActivePower())
                .loadFlowResult(LoadFlowResultEntity.builder().resultUuid(resultUuid).build())
                .build();
        componentResultEntity.setSlackBusResults(getSlackBusResultEntity(componentResult.getSlackBusResults()));
        return componentResultEntity;
    }

    private static List<SlackBusResultEntity> getSlackBusResultEntity(List<LoadFlowResult.SlackBusResult> slackBusResults) {
        return slackBusResults.stream()
                .map(slackBusResult -> SlackBusResultEntity.toEntity(slackBusResult.getId(), slackBusResult.getActivePowerMismatch())).toList();
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, LoadFlowStatus status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Override
    @Transactional
    public void insertStatus(List<UUID> resultUuids, LoadFlowStatus status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
                .map(uuid -> toStatusEntity(uuid, status)).toList());
    }

    @Transactional
    public void insert(UUID resultUuid,
                       LoadFlowResult result,
                       LoadFlowStatus status,
                       InitialValuesInfos initialValuesInfos,
                       List<LimitViolationInfos> limitViolationInfos) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            resultRepository.save(toResultEntity(resultUuid, result, initialValuesToJsonString(initialValuesInfos), limitViolationInfos));
        }
        globalStatusRepository.save(toStatusEntity(resultUuid, status));
    }

    private static LimitViolationEntity toLimitViolationsEntity(UUID resultUuid, LimitViolationInfos limitViolationInfos) {
        return LimitViolationEntity.builder()
                .loadFlowResult(LoadFlowResultEntity.builder().resultUuid(resultUuid).build())
                .subjectId(limitViolationInfos.getSubjectId())
                .locationId(limitViolationInfos.getLocationId())
                .limitType(limitViolationInfos.getLimitType())
                .limit(limitViolationInfos.getLimit())
                .limitName(limitViolationInfos.getLimitName())
                .actualOverload(limitViolationInfos.getActualOverloadDuration())
                .upComingOverload(limitViolationInfos.getUpComingOverloadDuration())
                .overload(limitViolationInfos.getOverload())
                .value(limitViolationInfos.getValue())
                .side(limitViolationInfos.getSide())
                .build();

    }

    @Override
    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        resultRepository.deleteByResultUuid(resultUuid);
    }

    public Optional<LoadFlowResultEntity> findResults(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultRepository.findByResultUuid(resultUuid);
    }

    @Override
    @Transactional
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        resultRepository.deleteAll();
    }

    @Override
    @Transactional(readOnly = true)
    public LoadFlowStatus findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        return globalEntity != null ? globalEntity.getStatus() : null;
    }

    public List<ComponentResultEntity> findComponentResults(UUID resultUuid, List<ResourceFilterDTO> resourceFilters, Sort sort) {
        Objects.requireNonNull(resultUuid);
        Specification<ComponentResultEntity> specification = componentResultSpecificationBuilder.buildSpecification(resultUuid, resourceFilters);
        return componentResultRepository.findAll(specification, sort);
    }

    private List<LimitViolationEntity> findLimitViolationsEntities(UUID limitViolationUuid, List<ResourceFilterDTO> resourceFilters, Sort sort) {
        Specification<LimitViolationEntity> specification = limitViolationsSpecificationBuilder.buildSpecification(limitViolationUuid, resourceFilters);
        return limitViolationRepository.findAll(specification, sort);
    }

    @Transactional(readOnly = true)
    public org.gridsuite.loadflow.server.dto.LoadFlowResult getResult(UUID resultUuid, String stringFilters, Sort sort) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        Objects.requireNonNull(resultUuid);
        org.gridsuite.loadflow.server.dto.LoadFlowResult loadFlowResult;
        LoadFlowResultEntity loadFlowResultEntity = findResults(resultUuid).orElse(null);
        if (loadFlowResultEntity == null) {
            return null;
        }
        List<ResourceFilterDTO> resourceFilters = fromStringFiltersToDTO(stringFilters, objectMapper);
        List<ComponentResultEntity> componentResults = findComponentResults(resultUuid, resourceFilters, sort);
        boolean hasChildFilter = resourceFilters.stream().anyMatch(slackBusResultSpecificationBuilder::isNotParentFilter);
        List<SlackBusResultEntity> slackBusResultEntities = new ArrayList<>();
        if (hasChildFilter) {
            slackBusResultEntities.addAll(findSlackBusResults(componentResults, resourceFilters));
        }
        loadFlowResultEntity.setComponentResults(componentResults);
        loadFlowResult = fromEntity(loadFlowResultEntity, slackBusResultEntities, hasChildFilter);
        LOGGER.info("Get LoadFlow Results {} in {}ms", resultUuid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get()));
        return loadFlowResult;
    }

    @Transactional(readOnly = true)
    public InitialValuesInfos getInitialValues(UUID resultUuid) {
        LoadFlowResultEntity loadFlowResultEntity = findResults(resultUuid).orElse(null);
        if (loadFlowResultEntity == null) {
            return null;
        }

        return initialValuesToDTO(loadFlowResultEntity.getInitialValues());
    }

    private static org.gridsuite.loadflow.server.dto.LoadFlowResult fromEntity(LoadFlowResultEntity resultEntity, List<SlackBusResultEntity> slackBusResultEntities, boolean hasChildFilter) {
        return org.gridsuite.loadflow.server.dto.LoadFlowResult.builder()
                .resultUuid(resultEntity.getResultUuid())
                .writeTimeStamp(resultEntity.getWriteTimeStamp())
                .componentResults(resultEntity.getComponentResults().stream().map(result -> LoadFlowService.fromEntity(result, slackBusResultEntities, hasChildFilter)).toList())
                .build();
    }

    public List<SlackBusResultEntity> findSlackBusResults(List<ComponentResultEntity> componentResultEntities, List<ResourceFilterDTO> resourceFilters) {
        List<UUID> componentResultUuids = componentResultEntities.stream()
                    .map(ComponentResultEntity::getComponentResultUuid)
                    .toList();
        Specification<SlackBusResultEntity> specificationSlack = slackBusResultSpecificationBuilder.buildSlackBusResultSpecification(componentResultUuids, resourceFilters);
        return slackBusResultRepository.findAll(specificationSlack);
    }

    public List<LoadFlowResult.ComponentResult.Status> findComputingStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return componentResultRepository.findComputingStatus(resultUuid);
    }

    @Transactional(readOnly = true)
    public List<LimitViolationInfos> getLimitViolationsInfos(UUID resultUuid, String stringFilters, String stringGlobalFilters, Sort sort, UUID networkUuid, String variantId) {
        if (!limitViolationRepository.existsLimitViolationEntitiesByLoadFlowResultResultUuid(resultUuid)) {
            return List.of();
        }

        // get resource filters and global filters
        List<ResourceFilterDTO> resourceFilters = fromStringFiltersToDTO(stringFilters, objectMapper);
        GlobalFilter globalFilter = FilterUtils.fromStringGlobalFiltersToDTO(stringGlobalFilters, objectMapper);
        if (globalFilter != null) {
            Optional<ResourceFilterDTO> resourceGlobalFilters = filterService.getResourceFilter(networkUuid, variantId, globalFilter);
            if (resourceGlobalFilters.isPresent()) {
                resourceFilters.add(resourceGlobalFilters.get());
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

    public List<LimitViolationEntity> findLimitViolations(UUID resultUuid, List<ResourceFilterDTO> resourceFilters, Sort sort) {
        Objects.requireNonNull(resultUuid);
        return findLimitViolationsEntities(resultUuid, resourceFilters, sort);
    }

    private InitialValuesInfos initialValuesToDTO(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, InitialValuesInfos.class);
        } catch (JsonProcessingException e) {
            throw new ComputationException("Invalid json string for initial values !");
        }
    }

    private String initialValuesToJsonString(InitialValuesInfos initialValuesInfos) {
        try {
            return objectMapper.writeValueAsString(initialValuesInfos);
        } catch (JsonProcessingException e) {
            throw new ComputationException("Invalid initial values for json string !");
        }
    }
}
