/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.loadflow.LoadFlowParameters;
import lombok.Getter;
import lombok.NonNull;
import org.gridsuite.loadflow.server.dto.parameters.LimitReductionsByVoltageLevel;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.dto.parameters.ParameterDifference;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowSpecificParameterEntity;
import org.gridsuite.loadflow.server.repositories.parameters.LoadFlowParametersRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Service
public class LoadFlowParametersService {

    private static final Float DEFAULT_LIMIT_REDUCTION_VALUE = 0.8f;

    private final LoadFlowParametersRepository loadFlowParametersRepository;

    private final LimitReductionService limitReductionService;

    @Getter
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

    private Optional<Float> getLimitReductionValueForProvider(LoadFlowParametersEntity entity) {
        // Only for providers other than OpenLoadFlow
        if (!limitReductionService.getProviders().contains(entity.getProvider())) {
            return Optional.of(entity.getLimitReduction() != null ? entity.getLimitReduction() : DEFAULT_LIMIT_REDUCTION_VALUE);
        }
        return Optional.empty();
    }

    private Float getDefaultLimitReductionValueForProvider(String provider) {
        return !limitReductionService.getProviders().contains(provider) ? DEFAULT_LIMIT_REDUCTION_VALUE : null;
    }

    public UUID createParameters(LoadFlowParametersInfos parametersInfos) {
        return loadFlowParametersRepository.save(parametersInfos.toEntity()).getId();
    }

    private void computeCommonParametersDifferences(LoadFlowParameters loadFlowParameters,
                                                    LoadFlowParameters referenceLoadFlowParameters,
                                                    Map<String, ParameterDifference> parametersDifferences) {
        Map<String, Object> mapParameters = loadFlowParameters.toMap();
        Map<String, Object> mapReferenceParameters = referenceLoadFlowParameters.toMap();
        mapParameters.forEach((parameterName, parameterValue) -> {
            if (mapReferenceParameters.containsKey(parameterName)) {
                Object referenceValue = mapReferenceParameters.get(parameterName);
                if (!Objects.equals(parameterValue, referenceValue)) {
                    parametersDifferences.put(parameterName, new ParameterDifference(parameterValue, referenceValue));
                }
            }
        });
    }

    private void computeSpecificParametersDifferences(Map<String, String> specificParameters,
                                                      Map<String, String> referenceSpecificParameters,
                                                      Map<String, ParameterDifference> parametersDifferences) {
        specificParameters.forEach((parameterName, parameterValue) -> {
            if (referenceSpecificParameters.containsKey(parameterName)) {
                String referenceValue = referenceSpecificParameters.get(parameterName);
                if (!Objects.equals(parameterValue, referenceValue)) {
                    parametersDifferences.put(parameterName, new ParameterDifference(parameterValue, referenceValue));
                }
            }
        });
    }

    private LoadFlowParametersInfos computeDifferences(LoadFlowParametersInfos loadFlowParametersInfos,
                                                       LoadFlowParameters referenceLoadFlowParameters,
                                                       Map<String, String> referenceSpecificParameters) {
        Map<String, ParameterDifference> parametersDifferences = new HashMap<>();

        computeCommonParametersDifferences(loadFlowParametersInfos.getCommonParameters(), referenceLoadFlowParameters, parametersDifferences);

        String provider = loadFlowParametersInfos.getProvider();
        Map<String, String> specificParameters = loadFlowParametersInfos.getSpecificParametersPerProvider().getOrDefault(provider, Collections.emptyMap());
        computeSpecificParametersDifferences(specificParameters, referenceSpecificParameters, parametersDifferences);

        if (!parametersDifferences.isEmpty()) {
            loadFlowParametersInfos.setParametersDifferences(parametersDifferences);
        }
        return loadFlowParametersInfos;
    }

    @Transactional(readOnly = true)
    public Optional<LoadFlowParametersInfos> getParameters(UUID parametersUuid, boolean withDifferences, UUID referenceParametersUuid) {
        Optional<LoadFlowParametersInfos> loadFlowParametersInfos = loadFlowParametersRepository.findById(parametersUuid).map(this::toLoadFlowParametersInfos);
        if (!withDifferences) {
            return loadFlowParametersInfos;
        }

        Optional<LoadFlowParametersInfos> result = Optional.empty();
        if (loadFlowParametersInfos.isPresent()) {
            String provider = loadFlowParametersInfos.get().getProvider();

            LoadFlowParameters referenceLoadFlowParameters;
            Map<String, String> referenceSpecificParameters;
            if (referenceParametersUuid != null) {
                Optional<LoadFlowParametersInfos> referenceLoadFlowParametersInfos = loadFlowParametersRepository.findById(referenceParametersUuid).map(this::toLoadFlowParametersInfos);
                if (referenceLoadFlowParametersInfos.isPresent()) {
                    referenceLoadFlowParameters = referenceLoadFlowParametersInfos.get().getCommonParameters();
                    referenceSpecificParameters = referenceLoadFlowParametersInfos.get().getSpecificParametersPerProvider().getOrDefault(provider, new HashMap<>());
                } else {
                    referenceSpecificParameters = new HashMap<>();
                    referenceLoadFlowParameters = LoadFlowParameters.load();
                }
            } else {
                referenceSpecificParameters = new HashMap<>();
                referenceLoadFlowParameters = LoadFlowParameters.load();
            }

            // merge reference-specific parameters with default-specific parameters only for the current provider
            List<com.powsybl.commons.parameters.Parameter> specificParameters = LoadFlowService.getSpecificLoadFlowParameters(provider).getOrDefault(provider, Collections.emptyList());
            specificParameters.forEach(parameter -> {
                String parameterName = parameter.getNames().getFirst();
                String defaultValue = parameter.getDefaultValue() == null ? null : String.valueOf(parameter.getDefaultValue());
                referenceSpecificParameters.putIfAbsent(parameterName, defaultValue);
            });

            // compute all the differences between loadflow parameters and reference loadflow parameters
            return Optional.of(computeDifferences(loadFlowParametersInfos.get(),
                referenceLoadFlowParameters,
                referenceSpecificParameters));
        }
        return result;
    }

    @Transactional(readOnly = true)
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
        //if the parameters is null it means it's a reset to defaultValues
        if (parametersInfos == null) {
            loadFlowParametersEntity.update(getDefaultParametersValues());
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
        LoadFlowParametersInfos defaultParametersInfos = getDefaultParametersValues();
        return createParameters(defaultParametersInfos);
    }

    public LoadFlowParametersInfos getDefaultParametersValues() {
        return LoadFlowParametersInfos.builder()
                .provider(defaultProvider)
                .limitReduction(getDefaultLimitReductionValueForProvider(defaultProvider))
                .commonParameters(LoadFlowParameters.load())
                .specificParametersPerProvider(Map.of())
                .limitReductions(limitReductionService.createDefaultLimitReductions()).build();
    }

    public List<LimitReductionsByVoltageLevel> getDefaultLimitReductions() {
        return limitReductionService.createDefaultLimitReductions();
    }

    public LoadFlowParameters getDefaultCommonParameters() {
        return LoadFlowParameters.load();
    }

    public LoadFlowParametersInfos toLoadFlowParametersInfos(LoadFlowParametersEntity entity) {
        return LoadFlowParametersInfos.builder()
                .uuid(entity.getId())
                .provider(entity.getProvider())
                .limitReduction(getLimitReductionValueForProvider(entity).orElse(null))
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
                .limitReduction(getLimitReductionValueForProvider(entity).orElse(null))
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
                .limitReduction(getLimitReductionValueForProvider(entity).orElse(null))
                .commonParameters(entity.toLoadFlowParameters())
                .specificParameters(entity.getSpecificParameters().stream()
                        .filter(p -> p.getProvider().equalsIgnoreCase(provider))
                        .collect(Collectors.toMap(LoadFlowSpecificParameterEntity::getName,
                                LoadFlowSpecificParameterEntity::getValue)))
                .limitReductions(getLimitReductionsForProvider(entity).orElse(null))
                .build();
    }

    @Transactional(readOnly = true)
    public String getProvider(UUID parametersUuid) {
        return loadFlowParametersRepository.findById(parametersUuid).map(LoadFlowParametersEntity::getProvider).orElseThrow();
    }
}
