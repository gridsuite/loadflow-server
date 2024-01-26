/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service.parameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.gridsuite.loadflow.server.repositories.parameters.LoadFlowParametersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powsybl.loadflow.LoadFlowParameters;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Service
public class LoadFlowParametersService {

    private final LoadFlowParametersRepository loadFlowParametersRepository;

    public LoadFlowParametersService(LoadFlowParametersRepository loadFlowParametersRepository) {
        this.loadFlowParametersRepository = loadFlowParametersRepository;
    }

    public UUID createParameters(LoadFlowParametersInfos parametersInfos) {
        return loadFlowParametersRepository.save(parametersInfos.toEntity()).getId();
    }

    public Optional<LoadFlowParametersInfos> getParameters(UUID parametersUuid) {
        return loadFlowParametersRepository.findById(parametersUuid).map(LoadFlowParametersEntity::toLoadFlowParametersInfos);
    }

    public Optional<LoadFlowParametersValues> getParametersValues(UUID parametersUuid, String provider) {
        return loadFlowParametersRepository.findById(parametersUuid)
            .map(entity -> entity.toLoadFlowParametersValues(provider));
    }

    public List<LoadFlowParametersInfos> getAllParameters() {
        return loadFlowParametersRepository.findAll().stream().map(LoadFlowParametersEntity::toLoadFlowParametersInfos).toList();
    }

    @Transactional
    public void updateParameters(UUID parametersUuid, LoadFlowParametersInfos parametersInfos) {
        loadFlowParametersRepository.findById(parametersUuid).orElseThrow().update(parametersInfos);
    }

    public void deleteParameters(UUID parametersUuid) {
        loadFlowParametersRepository.deleteById(parametersUuid);
    }

    @Transactional
    public Optional<UUID> duplicateParameters(UUID sourceParametersUuid) {
        return loadFlowParametersRepository.findById(sourceParametersUuid)
            .map(LoadFlowParametersEntity::copy)
            .map(loadFlowParametersRepository::save)
            .map(LoadFlowParametersEntity::getId);
    }

    public UUID createDefaultParameters() {
        //default parameters
        LoadFlowParametersInfos defaultParametersInfos = LoadFlowParametersInfos.builder()
            .commonParameters(LoadFlowParameters.load())
            .specificParametersPerProvider(Map.of())
            .build();
        return createParameters(defaultParametersInfos);
    }
}
