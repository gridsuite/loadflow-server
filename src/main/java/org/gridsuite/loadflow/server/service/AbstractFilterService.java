/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.FilterLoader;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.*;
import org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.FilterServiceUtils;
import org.gridsuite.filter.utils.expertfilter.CombinatorType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */
// TODO: This is a temporary implementation - this class should be deleted when
// AbstractFilterService is moved to the shared library
public abstract class AbstractFilterService implements FilterLoader {
    protected static final String FILTERS_NOT_FOUND = "Filters not found";
    protected static final String FILTER_API_VERSION = "v1";
    protected static final String DELIMITER = "/";

    protected final RestTemplate restTemplate = new RestTemplate();
    protected final NetworkStoreService networkStoreService;
    protected final String filterServerBaseUri;

    public static final String IDS = "ids";

    protected AbstractFilterService(NetworkStoreService networkStoreService, String filterServerBaseUri) {
        this.networkStoreService = networkStoreService;
        this.filterServerBaseUri = filterServerBaseUri;
    }

    @Override
    public List<AbstractFilter> getFilters(List<UUID> filtersUuids) {
        if (CollectionUtils.isEmpty(filtersUuids)) {
            return List.of();
        }

        String ids = filtersUuids.stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + FILTER_API_VERSION + "/filters/metadata")
                .queryParam(IDS, ids)
                .buildAndExpand()
                .toUriString();

        try {
            return restTemplate.exchange(
                    filterServerBaseUri + path,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<AbstractFilter>>() {
                    }
            ).getBody();
        } catch (HttpStatusCodeException e) {
            throw new PowsyblException(FILTERS_NOT_FOUND + " [" + filtersUuids + "]");
        }
    }

    public Network getNetwork(UUID networkUuid, String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            network.getVariantManager().setWorkingVariant(variantId);
            return network;
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public List<String> filterNetwork(AbstractFilter filter, Network network) {
        return FilterServiceUtils.getIdentifiableAttributes(filter, network, this)
                .stream()
                .map(IdentifiableAttributes::getId)
                .toList();
    }

    public Optional<ResourceFilterDTO> getResourceFilter(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter,
                                                         List<EquipmentType> equipmentTypes, String columnName) {

        Network network = getNetwork(networkUuid, variantId);
        List<AbstractFilter> genericFilters = getFilters(globalFilter.getGenericFilter());

        // Filter equipments by type
        Map<EquipmentType, List<String>> subjectIdsByEquipmentType = filterEquipmentsByType(
                network, globalFilter, genericFilters, equipmentTypes
        );

        // Combine all results into one list
        List<String> subjectIds = subjectIdsByEquipmentType.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();

        return subjectIds.isEmpty() ? Optional.empty() :
                Optional.of(new ResourceFilterDTO(
                        ResourceFilterDTO.DataType.TEXT,
                        ResourceFilterDTO.Type.IN,
                        subjectIds,
                        columnName
                ));
    }

    public List<AbstractExpertRule> createNumberExpertRules(List<String> values, FieldType fieldType) {
        List<AbstractExpertRule> rules = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                rules.add(NumberExpertRule.builder()
                        .value(Double.valueOf(value))
                        .field(fieldType)
                        .operator(OperatorType.EQUALS)
                        .build());
            }
        }
        return rules;
    }

    public AbstractExpertRule createPropertiesRule(String property, List<String> propertiesValues, FieldType fieldType) {
        return PropertiesExpertRule.builder()
                .combinator(CombinatorType.OR)
                .operator(OperatorType.IN)
                .field(fieldType)
                .propertyName(property)
                .propertyValues(propertiesValues)
                .build();
    }

    public List<AbstractExpertRule> createEnumExpertRules(List<Country> values, FieldType fieldType) {
        List<AbstractExpertRule> rules = new ArrayList<>();
        if (values != null) {
            for (Country value : values) {
                rules.add(EnumExpertRule.builder()
                        .value(value.toString())
                        .field(fieldType)
                        .operator(OperatorType.EQUALS)
                        .build());
            }
        }
        return rules;
    }

    public AbstractExpertRule createCombination(CombinatorType combinatorType, List<AbstractExpertRule> rules) {
        return CombinatorExpertRule.builder().combinator(combinatorType).rules(rules).build();
    }

    public Optional<AbstractExpertRule> createOrCombination(List<AbstractExpertRule> rules) {
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rules.size() > 1 ? createCombination(CombinatorType.OR, rules) : rules.getFirst());
    }

    /**
     * Extracts equipment IDs from a generic filter based on equipment type
     */
    public List<String> extractEquipmentIdsFromGenericFilter(
            AbstractFilter filter,
            EquipmentType targetEquipmentType,
            Network network) {

        if (filter.getEquipmentType() == targetEquipmentType) {
            return filterNetwork(filter, network);
        } else if (filter.getEquipmentType() == EquipmentType.VOLTAGE_LEVEL) {
            ExpertFilter voltageFilter = buildExpertFilterWithVoltageLevelIdsCriteria(
                    filter.getId(), targetEquipmentType);
            return filterNetwork(voltageFilter, network);
        }
        return List.of();
    }

    /**
     * Combines multiple filter results using AND or OR logic
     */
    public List<String> combineFilterResults(List<List<String>> filterResults, boolean useAndLogic) {
        if (filterResults.isEmpty()) {
            return List.of();
        }

        if (filterResults.size() == 1) {
            return filterResults.getFirst();
        }

        if (useAndLogic) {
            // Intersection of all results
            Set<String> result = new HashSet<>(filterResults.getFirst());
            for (int i = 1; i < filterResults.size(); i++) {
                result.retainAll(filterResults.get(i));
            }
            return new ArrayList<>(result);
        } else {
            // Union of all results
            Set<String> result = new HashSet<>();
            filterResults.forEach(result::addAll);
            return new ArrayList<>(result);
        }
    }

    /**
     * Extracts filtered equipment IDs by applying expert and generic filters
     */
    public List<String> extractFilteredEquipmentIds(
            Network network,
            GlobalFilter globalFilter,
            List<AbstractFilter> genericFilters,
            EquipmentType equipmentType) {

        List<List<String>> allFilterResults = new ArrayList<>();

        // Extract IDs from expert filter
        ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
        if (expertFilter != null) {
            allFilterResults.add(filterNetwork(expertFilter, network));
        }

        // Extract IDs from generic filters
        for (AbstractFilter filter : genericFilters) {
            List<String> filterResult = extractEquipmentIdsFromGenericFilter(filter, equipmentType, network);
            if (!filterResult.isEmpty()) {
                allFilterResults.add(filterResult);
            }
        }

        // Combine results with appropriate logic
        // Expert filters use OR between them, generic filters use AND
        return combineFilterResults(allFilterResults, !genericFilters.isEmpty());
    }

    /**
     * Builds expert filter with voltage level IDs criteria
     */
    public ExpertFilter buildExpertFilterWithVoltageLevelIdsCriteria(UUID filterUuid, EquipmentType equipmentType) {
        AbstractExpertRule voltageLevelId1Rule = createVoltageLevelIdRule(filterUuid, TwoSides.ONE);
        AbstractExpertRule voltageLevelId2Rule = createVoltageLevelIdRule(filterUuid, TwoSides.TWO);
        AbstractExpertRule orCombination = createCombination(CombinatorType.OR,
                List.of(voltageLevelId1Rule, voltageLevelId2Rule));
        return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, orCombination);
    }

    /**
     * Creates voltage level ID rule for filtering
     */
    public AbstractExpertRule createVoltageLevelIdRule(UUID filterUuid, TwoSides side) {
        return FilterUuidExpertRule.builder()
                .operator(OperatorType.IS_PART_OF)
                .field(side == TwoSides.ONE ? FieldType.VOLTAGE_LEVEL_ID_1 : FieldType.VOLTAGE_LEVEL_ID_2)
                .values(Set.of(filterUuid.toString()))
                .build();
    }

    /**
     * Builds all expert rules for a global filter and equipment type
     */
    public List<AbstractExpertRule> buildAllExpertRules(GlobalFilter globalFilter, EquipmentType equipmentType) {
        List<AbstractExpertRule> andRules = new ArrayList<>();

        // Nominal voltage rules
        buildNominalVoltageRules(globalFilter.getNominalV(), equipmentType)
                .ifPresent(andRules::add);

        // Country code rules
        buildCountryCodeRules(globalFilter.getCountryCode(), equipmentType)
                .ifPresent(andRules::add);

        // Substation property rules
        if (globalFilter.getSubstationProperty() != null) {
            buildSubstationPropertyRules(globalFilter.getSubstationProperty(), equipmentType)
                    .ifPresent(andRules::add);
        }

        return andRules;
    }

    /**
     * Builds nominal voltage rules combining all relevant field types
     */
    public Optional<AbstractExpertRule> buildNominalVoltageRules(
            List<String> nominalVoltages, EquipmentType equipmentType) {

        List<FieldType> fieldTypes = getNominalVoltageFieldType(equipmentType);
        List<AbstractExpertRule> rules = fieldTypes.stream()
                .flatMap(fieldType -> createNumberExpertRules(nominalVoltages, fieldType).stream())
                .toList();

        return createOrCombination(rules);
    }

    /**
     * Builds country code rules combining all relevant field types
     */
    public Optional<AbstractExpertRule> buildCountryCodeRules(
            List<Country> countryCodes, EquipmentType equipmentType) {

        List<FieldType> fieldTypes = getCountryCodeFieldType(equipmentType);
        List<AbstractExpertRule> rules = fieldTypes.stream()
                .flatMap(fieldType -> createEnumExpertRules(countryCodes, fieldType).stream())
                .toList();

        return createOrCombination(rules);
    }

    /**
     * Builds substation property rules combining all relevant field types
     */
    public Optional<AbstractExpertRule> buildSubstationPropertyRules(
            Map<String, List<String>> properties, EquipmentType equipmentType) {

        List<FieldType> fieldTypes = getSubstationPropertiesFieldTypes(equipmentType);
        List<AbstractExpertRule> rules = properties.entrySet().stream()
                .flatMap(entry -> fieldTypes.stream()
                        .map(fieldType -> createPropertiesRule(
                                entry.getKey(), entry.getValue(), fieldType)))
                .toList();

        return createOrCombination(rules);
    }

    /**
     * Filters equipments by type and returns map of IDs grouped by equipment type
     */
    public Map<EquipmentType, List<String>> filterEquipmentsByType(
            Network network,
            GlobalFilter globalFilter,
            List<AbstractFilter> genericFilters,
            List<EquipmentType> equipmentTypes) {

        Map<EquipmentType, List<String>> result = new EnumMap<>(EquipmentType.class);

        for (EquipmentType equipmentType : equipmentTypes) {
            List<String> filteredIds = extractFilteredEquipmentIds(network, globalFilter, genericFilters, equipmentType);
            if (!filteredIds.isEmpty()) {
                result.put(equipmentType, filteredIds);
            }
        }

        return result;
    }

    /**
     * Builds expert filter from global filter and equipment type
     */
    public ExpertFilter buildExpertFilter(GlobalFilter globalFilter, EquipmentType equipmentType) {
        List<AbstractExpertRule> andRules = buildAllExpertRules(globalFilter, equipmentType);

        return andRules.isEmpty() ? null :
                new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType,
                        createCombination(CombinatorType.AND, andRules));
    }

    public List<FieldType> getNominalVoltageFieldType(EquipmentType equipmentType) {
        return switch (equipmentType) {
            case LINE, TWO_WINDINGS_TRANSFORMER -> List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2);
            case VOLTAGE_LEVEL -> List.of(FieldType.NOMINAL_VOLTAGE);
            default -> List.of();
        };
    }

    public List<FieldType> getCountryCodeFieldType(EquipmentType equipmentType) {
        return switch (equipmentType) {
            case VOLTAGE_LEVEL, TWO_WINDINGS_TRANSFORMER -> List.of(FieldType.COUNTRY);
            case LINE -> List.of(FieldType.COUNTRY_1, FieldType.COUNTRY_2);
            default -> List.of();
        };
    }

    public List<FieldType> getSubstationPropertiesFieldTypes(EquipmentType equipmentType) {
        return equipmentType == EquipmentType.LINE ?
                List.of(FieldType.SUBSTATION_PROPERTIES_1, FieldType.SUBSTATION_PROPERTIES_2) :
                List.of(FieldType.SUBSTATION_PROPERTIES);
    }
}
