/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import java.util.*;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.gridsuite.loadflow.server.repositories.parameters.LoadFlowParametersRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.powsybl.loadflow.LoadFlowParameters;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Service
public class LoadFlowParametersService {

    private final String defaultLoadflowProvider;

    private final LoadFlowParametersRepository loadFlowParametersRepository;

    private final LimitReductionService limitReductionService;

    public LoadFlowParametersService(LoadFlowParametersRepository loadFlowParametersRepository,
                                     @Value("${loadflow.default-provider}") String defaultLoadflowProvider, LimitReductionService limitReductionService) {
        this.loadFlowParametersRepository = loadFlowParametersRepository;
        this.defaultLoadflowProvider = defaultLoadflowProvider;
        this.limitReductionService = limitReductionService;
    }

    public UUID createParameters(LoadFlowParametersInfos parametersInfos) {
        return loadFlowParametersRepository.save(parametersInfos.toEntity()).getId();
    }

    public Optional<LoadFlowParametersInfos> getParameters(UUID parametersUuid) {
        return loadFlowParametersRepository.findById(parametersUuid).map(LoadFlowParametersEntity::toLoadFlowParametersInfos);
    }

    public Optional<LoadFlowParametersValues> getParametersValues(UUID parametersUuid, String provider) {
        return loadFlowParametersRepository.findById(parametersUuid).map(entity -> entity.toLoadFlowParametersValues(provider));
    }

    public LoadFlowParametersValues getParametersValues(UUID parametersUuid) {
        return loadFlowParametersRepository.findById(parametersUuid)
            .map(LoadFlowParametersEntity::toLoadFlowParametersValues)
                .map(p -> {
                    if (p.getLimitReductionsValues() != null && p.getProvider().equals("OpenLoadFlow")) {
                        p.setLimitReductions(limitReductionService.createLimitReductions(p.getLimitReductionsValues()));
                    }
                    return p;
                })
                .orElseThrow();
    }

    public List<LoadFlowParametersInfos> getAllParameters() {
        return loadFlowParametersRepository.findAll().stream().map(LoadFlowParametersEntity::toLoadFlowParametersInfos).toList();
    }

    @Transactional
    public void updateParameters(UUID parametersUuid, LoadFlowParametersInfos parametersInfos) {
        LoadFlowParametersEntity loadFlowParametersEntity = loadFlowParametersRepository.findById(parametersUuid).orElseThrow();
        //if the parameters is null it means it's a reset to defaultValues but we need to keep the provider because it's updated separately
        if (parametersInfos == null) {
            loadFlowParametersEntity.update(getDefaultParametersValues(loadFlowParametersEntity.getProvider(), limitReductionService));
        } else {
            loadFlowParametersEntity.update(parametersInfos);
        }
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
        LoadFlowParametersInfos defaultParametersInfos = getDefaultParametersValues(defaultLoadflowProvider, limitReductionService);
        return createParameters(defaultParametersInfos);
    }

    public LoadFlowParametersInfos getDefaultParametersValues(String provider, LimitReductionService limitReductionService) {
        List<List<Double>> limitReductions = Optional.ofNullable(limitReductionService)
                .map(LimitReductionService::getDefaultValues)
                .orElseGet(Collections::emptyList);
        return LoadFlowParametersInfos.builder()
            .provider(provider)
            .commonParameters(LoadFlowParameters.load())
            .specificParametersPerProvider(Map.of())
            .limitReductionsValues(limitReductions)
            .build();
    }

    @Transactional
    public void updateProvider(UUID parametersUuid, String provider) {
        loadFlowParametersRepository.findById(parametersUuid)
            .orElseThrow()
            .setProvider(provider != null ? provider : defaultLoadflowProvider);
    }
}
