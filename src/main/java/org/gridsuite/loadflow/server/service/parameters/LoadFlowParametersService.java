/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service.parameters;

import java.util.List;
import java.util.UUID;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.gridsuite.loadflow.server.repositories.parameters.LoadFlowParametersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return loadFlowParametersRepository.save(parametersInfos.toEntity()).toLoadFlowParametersInfos().getUuid();
    }

    public LoadFlowParametersInfos getParameters(UUID parametersUuid) {
        return loadFlowParametersRepository.findById(parametersUuid).map(LoadFlowParametersEntity::toLoadFlowParametersInfos).orElse(null);
    }

    public LoadFlowParametersValues getParametersValues(UUID parametersUuid, String provider) {
        LoadFlowParametersEntity parametersEntity = loadFlowParametersRepository.findById(parametersUuid).orElse(null);
        return parametersEntity != null ? parametersEntity.toLoadFlowParametersValues(provider) : null;
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
}
