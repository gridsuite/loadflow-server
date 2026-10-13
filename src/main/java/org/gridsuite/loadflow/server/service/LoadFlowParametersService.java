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

    @Transactional(readOnly = true)
    public Optional<LoadFlowParametersInfos> getParameters(UUID parametersUuid, boolean withDifferences, UUID referenceParametersUuid) {
        return loadFlowParametersRepository.findById(parametersUuid)
            .map(this::toLoadFlowParametersInfos)
            .map(parameters -> {
                if (!withDifferences) {
                    return parameters;
                }

                LoadFlowParametersInfos reference = Optional.ofNullable(referenceParametersUuid)
                    .flatMap(loadFlowParametersRepository::findById)
                    .map(this::toLoadFlowParametersInfos)
                    .orElseGet(this::getDefaultParametersValues);
                return computeDifferences(parameters, reference);
            });
    }

    private LoadFlowParametersInfos computeDifferences(LoadFlowParametersInfos parameters,
                                                       LoadFlowParametersInfos reference) {
        Map<String, ParameterDifference> differences = new HashMap<>();
        addDifferences(parameters.getCommonParameters().toMap(),
            reference.getCommonParameters().toMap(), differences);

        String provider = parameters.getProvider();
        Map<String, String> defaults = getSpecificParametersDefaults(provider);

        Map<String, String> values = new HashMap<>(defaults);
        values.putAll(parameters.getSpecificParametersPerProvider()
            .getOrDefault(provider, Collections.emptyMap()));

        Map<String, String> referenceValues = new HashMap<>(defaults);
        referenceValues.putAll(reference.getSpecificParametersPerProvider()
            .getOrDefault(provider, Collections.emptyMap()));

        addDifferences(values, referenceValues, differences);

        if (!differences.isEmpty()) {
            parameters.setParametersDifferences(differences);
        }
        return parameters;
    }

    private Map<String, String> getSpecificParametersDefaults(String provider) {
        Map<String, String> defaults = new HashMap<>();
        LoadFlowService.getSpecificLoadFlowParameters(provider)
            .getOrDefault(provider, Collections.emptyList())
            .forEach(parameter -> defaults.put(
                parameter.getNames().getFirst(),
                parameter.getDefaultValue() == null ? null : String.valueOf(parameter.getDefaultValue())));
        return defaults;
    }

    private void addDifferences(Map<String, ?> values, Map<String, ?> referenceValues,
                               Map<String, ParameterDifference> differences) {
        values.forEach((name, value) -> {
            if (referenceValues.containsKey(name) && !Objects.equals(value, referenceValues.get(name))) {
                differences.put(name, new ParameterDifference(value, referenceValues.get(name)));
            }
        });
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
