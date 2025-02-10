/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import java.util.*;
import java.util.stream.Collectors;

import lombok.NonNull;
import org.gridsuite.loadflow.server.dto.parameters.LimitReductionsByVoltageLevel;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowSpecificParameterEntity;
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

    private final LoadFlowParametersRepository loadFlowParametersRepository;

    private final LimitReductionService limitReductionService;

    private final String defaultProvider;

    public LoadFlowParametersService(@NonNull LoadFlowParametersRepository loadFlowParametersRepository,
            @Value("${loadflow.default-provider}") String defaultProvider, @NonNull LimitReductionService limitReductionService) {
        this.loadFlowParametersRepository = loadFlowParametersRepository;
        this.defaultProvider = defaultProvider;
        this.limitReductionService = limitReductionService;
    }

    private Optional<List<LimitReductionsByVoltageLevel>> getLimitReductionsForProvider(LoadFlowParametersEntity entity) {
        // Only for some providers
        if (!limitReductionService.getProviders().contains(entity.getProvider())) {
            return Optional.empty();
        }
        List<List<Double>> limitReductionsValues = entity.toLimitReductionsValues();
        return Optional.of(limitReductionsValues.isEmpty() ? limitReductionService.createDefaultLimitReductions() : limitReductionService.createLimitReductions(limitReductionsValues));
    }

    public UUID createParameters(LoadFlowParametersInfos parametersInfos) {
        return loadFlowParametersRepository.save(parametersInfos.toEntity()).getId();
    }

    public Optional<LoadFlowParametersInfos> getParameters(UUID parametersUuid) {
        return loadFlowParametersRepository.findById(parametersUuid).map(this::toLoadFlowParametersInfos);
    }

    public Optional<LoadFlowParametersValues> getParametersValues(UUID parametersUuid, String provider) {
        return loadFlowParametersRepository.findById(parametersUuid).map(entity -> toLoadFlowParametersValues(provider, entity));
    }

    public LoadFlowParametersValues getParametersValues(UUID parametersUuid) {
        return loadFlowParametersRepository.findById(parametersUuid)
                .map(this::toLoadFlowParametersValues).orElseThrow();
    }

    public List<LoadFlowParametersInfos> getAllParameters() {
        return loadFlowParametersRepository.findAll().stream().map(this::toLoadFlowParametersInfos).toList();
    }

    @Transactional
    public void updateParameters(UUID parametersUuid, LoadFlowParametersInfos parametersInfos) {
        LoadFlowParametersEntity loadFlowParametersEntity = loadFlowParametersRepository.findById(parametersUuid).orElseThrow();
        //if the parameters is null it means it's a reset to defaultValues, but we need to keep the provider because it's updated separately
        if (parametersInfos == null) {
            loadFlowParametersEntity.update(getDefaultParametersValues(loadFlowParametersEntity.getProvider()));
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
                .map(e -> toLoadFlowParametersInfos(e).toEntity())
            .map(loadFlowParametersRepository::save)
            .map(LoadFlowParametersEntity::getId);
    }

    public UUID createDefaultParameters() {
        //default parameters
        LoadFlowParametersInfos defaultParametersInfos = getDefaultParametersValues(defaultProvider);
        return createParameters(defaultParametersInfos);
    }

    public LoadFlowParametersInfos getDefaultParametersValues(String provider) {
        return LoadFlowParametersInfos.builder()
                .provider(provider)
                .commonParameters(LoadFlowParameters.load())
                .specificParametersPerProvider(Map.of())
                .limitReductions(limitReductionService.createDefaultLimitReductions()).build();
    }

    public List<LimitReductionsByVoltageLevel> getDefaultLimitReductions() {
        return limitReductionService.createDefaultLimitReductions();
    }

    @Transactional
    public void updateProvider(UUID parametersUuid, String provider) {
        loadFlowParametersRepository.findById(parametersUuid)
            .orElseThrow()
            .setProvider(provider != null ? provider : defaultProvider);
    }

    public LoadFlowParametersInfos toLoadFlowParametersInfos(LoadFlowParametersEntity entity) {
        return LoadFlowParametersInfos.builder()
                .uuid(entity.getId())
                .provider(entity.getProvider())
                .limitReduction(entity.getLimitReduction())
                .commonParameters(entity.toLoadFlowParameters())
                .specificParametersPerProvider(entity.getSpecificParameters().stream()
                        .collect(Collectors.groupingBy(LoadFlowSpecificParameterEntity::getProvider,
                                Collectors.toMap(LoadFlowSpecificParameterEntity::getName,
                                        LoadFlowSpecificParameterEntity::getValue))))
                .limitReductions(getLimitReductionsForProvider(entity).orElse(null))
                .build();
    }

    public LoadFlowParametersValues toLoadFlowParametersValues(LoadFlowParametersEntity entity) {
        return LoadFlowParametersValues.builder()
                .provider(entity.getProvider())
                .limitReduction(entity.getLimitReduction())
                .commonParameters(entity.toLoadFlowParameters())
                .specificParameters(entity.getSpecificParameters().stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase(entity.getProvider()))
                        .collect(Collectors.toMap(LoadFlowSpecificParameterEntity::getName,
                                LoadFlowSpecificParameterEntity::getValue)))
                .limitReductions(getLimitReductionsForProvider(entity).orElse(null))
                .build();
    }

    public LoadFlowParametersValues toLoadFlowParametersValues(String provider, LoadFlowParametersEntity entity) {
        return LoadFlowParametersValues.builder()
                .provider(provider)
                .limitReduction(entity.getLimitReduction())
                .commonParameters(entity.toLoadFlowParameters())
                .specificParameters(entity.getSpecificParameters().stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase(provider))
                        .collect(Collectors.toMap(LoadFlowSpecificParameterEntity::getName,
                                LoadFlowSpecificParameterEntity::getValue)))
                .limitReductions(getLimitReductionsForProvider(entity).orElse(null))
                .build();
    }
}
