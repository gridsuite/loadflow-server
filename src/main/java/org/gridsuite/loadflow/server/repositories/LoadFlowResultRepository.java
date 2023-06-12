/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories;

import com.powsybl.loadflow.LoadFlowResult;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.GlobalStatusEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Anis Touri <anis.touri at rte-france.com
 */
@Repository
public class LoadFlowResultRepository {

    private GlobalStatusRepository globalStatusRepository;

    private ResultRepository resultRepository;

    public LoadFlowResultRepository(GlobalStatusRepository globalStatusRepository,
                                    ResultRepository resultRepository) {
        this.globalStatusRepository = globalStatusRepository;
        this.resultRepository = resultRepository;
    }

    private static LoadFlowResultEntity toResultEntity(UUID resultUuid, LoadFlowResult result) {
        Set<ComponentResultEntity> componentResults = result.getComponentResults().stream().map(LoadFlowResultRepository::toComponentResultEntity).collect(Collectors.toSet());
        return new LoadFlowResultEntity(resultUuid, ZonedDateTime.now(ZoneOffset.UTC), componentResults);
    }

    private static ComponentResultEntity toComponentResultEntity(LoadFlowResult.ComponentResult componentResult) {
        int connectedComponentNum = componentResult.getConnectedComponentNum();
        int synchronousComponentNum = componentResult.getSynchronousComponentNum();
        String status = componentResult.getStatus().name();
        int iterationCount = componentResult.getIterationCount();
        String slackBusId = componentResult.getSlackBusId();
        double slackBusActivePowerMismatch = componentResult.getSlackBusActivePowerMismatch();
        double distributedActivePower = componentResult.getDistributedActivePower();
        ComponentResultEntity componentResultEntity = ComponentResultEntity.builder().connectedComponentNum(connectedComponentNum)
                .synchronousComponentNum(synchronousComponentNum)
                .status(status)
                .iterationCount(iterationCount)
                .slackBusId(slackBusId)
                .iterationCount(iterationCount)
                .slackBusId(slackBusId)
                .slackBusActivePowerMismatch(slackBusActivePowerMismatch)
                .distributedActivePower(distributedActivePower)
                .build();
        return componentResultEntity;
    }

    private static GlobalStatusEntity toStatusEntity(UUID resultUuid, String status) {
        return new GlobalStatusEntity(resultUuid, status);
    }

    @Transactional
    public void insertStatus(List<UUID> resultUuids, String status) {
        Objects.requireNonNull(resultUuids);
        globalStatusRepository.saveAll(resultUuids.stream()
                .map(uuid -> toStatusEntity(uuid, status)).collect(Collectors.toList()));
    }

    @Transactional
    public void insert(UUID resultUuid, LoadFlowResult result) {
        Objects.requireNonNull(resultUuid);
        if (result != null) {
            resultRepository.save(toResultEntity(resultUuid, result));
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
    public String findStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        GlobalStatusEntity globalEntity = globalStatusRepository.findByResultUuid(resultUuid);
        if (globalEntity != null) {
            return globalEntity.getStatus();
        } else {
            return null;
        }
    }

}
