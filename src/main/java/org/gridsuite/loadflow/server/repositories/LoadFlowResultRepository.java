/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories;

import com.powsybl.loadflow.LoadFlowResult;
import lombok.AllArgsConstructor;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.GlobalStatusEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Anis Touri <anis.touri at rte-france.com
 */
@AllArgsConstructor
@Repository
public class LoadFlowResultRepository {

    private GlobalStatusRepository globalStatusRepository;

    private ResultRepository resultRepository;

    private static LoadFlowResultEntity toResultEntity(UUID resultUuid, LoadFlowResult result) {
        Set<ComponentResultEntity> componentResults = result.getComponentResults().stream()
                .map(componentResult -> LoadFlowResultRepository.toComponentResultEntity(resultUuid, componentResult))
                .collect(Collectors.toSet());
        return new LoadFlowResultEntity(resultUuid, ZonedDateTime.now(ZoneOffset.UTC), componentResults);
    }

    private static ComponentResultEntity toComponentResultEntity(UUID resultUuid, LoadFlowResult.ComponentResult componentResult) {
        return ComponentResultEntity.builder().connectedComponentNum(componentResult.getConnectedComponentNum())
                .synchronousComponentNum(componentResult.getSynchronousComponentNum())
                .status(componentResult.getStatus())
                .iterationCount(componentResult.getIterationCount())
                .slackBusId(componentResult.getSlackBusId())
                .iterationCount(componentResult.getIterationCount())
                .slackBusId(componentResult.getSlackBusId())
                .slackBusActivePowerMismatch(componentResult.getSlackBusActivePowerMismatch())
                .distributedActivePower(componentResult.getDistributedActivePower())
                .loadFlowResult(LoadFlowResultEntity.builder().resultUuid(resultUuid).build())
                .build();
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, LoadFlowStatus status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, LoadFlowStatus status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
                .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    @Transactional
    public void insert(UUID resultUuid, LoadFlowResult result, LoadFlowStatus status) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            resultRepository.save(toResultEntity(resultUuid, result));
            globalStatusRepository.save(toStatusEntity(resultUuid, status));
        }
    }

    @Transactional
    public void delete(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        globalStatusRepository.deleteByResultUuid(resultUuid);
        resultRepository.deleteByResultUuid(resultUuid);
    }

    @Transactional(readOnly = true)
    public Optional<LoadFlowResultEntity> findResults(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        return resultRepository.findByResultUuid(resultUuid);
    }

    @Transactional
    public void deleteAll() {
        globalStatusRepository.deleteAll();
        resultRepository.deleteAll();
    }

    @Transactional(readOnly = true)
    public LoadFlowStatus findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        return globalEntity != null ? globalEntity.getStatus() : null;
    }

}
