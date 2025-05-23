/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
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
import com.powsybl.security.LimitViolationType;
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
import org.gridsuite.loadflow.server.dto.Column;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@Service
public class FilterService implements FilterLoader {
    public static final String FILTERS_NOT_FOUND = "Filters not found";

    private static final String FILTER_SERVER_API_VERSION = "v1";

    private static final String DELIMITER = "/";

    private static String filterServerBaseUri;

    private final RestTemplate restTemplate = new RestTemplate();

    private final NetworkStoreService networkStoreService;

    public FilterService(
            NetworkStoreService networkStoreService,
            @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri
    ) {
        this.networkStoreService = networkStoreService;
        setFilterServerBaseUri(filterServerBaseUri);
    }

    public static void setFilterServerBaseUri(String filterServerBaseUri) {
        FilterService.filterServerBaseUri = filterServerBaseUri;
    }

    public List<AbstractFilter> getFilters(List<UUID> filtersUuids) {
        if (CollectionUtils.isEmpty(filtersUuids)) {
            return List.of();
        }
        var ids = "?ids=" + filtersUuids.stream().map(UUID::toString).collect(Collectors.joining(","));
        String path = UriComponentsBuilder.fromPath(DELIMITER + FILTER_SERVER_API_VERSION + "/filters/metadata" + ids)
                .buildAndExpand()
                .toUriString();
        try {
            return restTemplate.exchange(filterServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<AbstractFilter>>() { }).getBody();
        } catch (HttpStatusCodeException e) {
            throw new PowsyblException(FILTERS_NOT_FOUND + " [" + filtersUuids + "]");
        }
    }

    private List<AbstractExpertRule> createNumberExpertRules(List<String> values, FieldType fieldType) {
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

    private AbstractExpertRule createPropertiesRule(String property, List<String> propertiesValues, FieldType fieldType) {
        return PropertiesExpertRule.builder()
            .combinator(CombinatorType.OR)
            .operator(OperatorType.IN)
            .field(fieldType)
            .propertyName(property)
            .propertyValues(propertiesValues)
            .build();
    }

    private List<AbstractExpertRule> createEnumExpertRules(List<Country> values, FieldType fieldType) {
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

    private List<AbstractExpertRule> createNominalVoltageRules(List<String> nominalVoltageList, List<FieldType> nominalFieldTypes) {
        List<AbstractExpertRule> nominalVoltageRules = new ArrayList<>();
        for (FieldType fieldType : nominalFieldTypes) {
            nominalVoltageRules.addAll(createNumberExpertRules(nominalVoltageList, fieldType));
        }
        return nominalVoltageRules;
    }

    private List<AbstractExpertRule> createCountryCodeRules(List<Country> countryCodeList, List<FieldType> countryCodeFieldTypes) {
        List<AbstractExpertRule> countryCodeRules = new ArrayList<>();
        for (FieldType fieldType : countryCodeFieldTypes) {
            countryCodeRules.addAll(createEnumExpertRules(countryCodeList, fieldType));
        }
        return countryCodeRules;
    }

    private List<AbstractExpertRule> createPropertiesRules(String property, List<String> propertiesValues, List<FieldType> propertiesFieldTypes) {
        List<AbstractExpertRule> propertiesRules = new ArrayList<>();
        for (FieldType fieldType : propertiesFieldTypes) {
            propertiesRules.add(createPropertiesRule(property, propertiesValues, fieldType));
        }
        return propertiesRules;
    }

    private List<FieldType> getNominalVoltageFieldType(EquipmentType equipmentType) {
        boolean isLineOrTwoWT = equipmentType.equals(EquipmentType.LINE) || equipmentType.equals(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        if (isLineOrTwoWT) {
            return List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2);
        }
        if (equipmentType.equals(EquipmentType.VOLTAGE_LEVEL)) {
            return List.of(FieldType.NOMINAL_VOLTAGE);
        }
        return List.of();
    }

    private List<FieldType> getCountryCodeFieldType(EquipmentType equipmentType) {
        boolean isLVoltageLevelOrTwoWT = equipmentType.equals(EquipmentType.VOLTAGE_LEVEL) || equipmentType.equals(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        if (isLVoltageLevelOrTwoWT) {
            return List.of(FieldType.COUNTRY);
        }
        if (equipmentType.equals(EquipmentType.LINE)) {
            return List.of(FieldType.COUNTRY_1, FieldType.COUNTRY_2);

        }
        return List.of();
    }

    private List<FieldType> getSubstationPropertiesFieldTypes(EquipmentType equipmentType) {
        if (equipmentType.equals(EquipmentType.LINE)) {
            return List.of(FieldType.SUBSTATION_PROPERTIES_1, FieldType.SUBSTATION_PROPERTIES_2);
        }
        return List.of(FieldType.SUBSTATION_PROPERTIES);
    }

    private ExpertFilter buildExpertFilter(GlobalFilter globalFilter, EquipmentType equipmentType) {
        List<AbstractExpertRule> andRules = new ArrayList<>();

        // among themselves the various global filter rules are OR combinated
        List<AbstractExpertRule> nominalVRules = createNominalVoltageRules(globalFilter.getNominalV(), getNominalVoltageFieldType(equipmentType));
        createOrCombination(nominalVRules).ifPresent(andRules::add);

        List<AbstractExpertRule> countryCodRules = createCountryCodeRules(globalFilter.getCountryCode(), getCountryCodeFieldType(equipmentType));
        createOrCombination(countryCodRules).ifPresent(andRules::add);

        if (globalFilter.getSubstationProperty() != null) {
            List<AbstractExpertRule> propertiesRules = new ArrayList<>();
            globalFilter.getSubstationProperty().forEach((propertyName, propertiesValues) ->
                    propertiesRules.addAll(createPropertiesRules(
                            propertyName,
                            propertiesValues,
                            getSubstationPropertiesFieldTypes(equipmentType)
                    )));
            createOrCombination(propertiesRules).ifPresent(andRules::add);
        }

        // between them the various global filter rules are AND combinated
        AbstractExpertRule andCombination = createCombination(CombinatorType.AND, andRules);

        return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, andCombination);
    }

    private AbstractExpertRule createCombination(CombinatorType combinatorType, List<AbstractExpertRule> rules) {
        return CombinatorExpertRule.builder().combinator(combinatorType).rules(rules).build();
    }

    private Optional<AbstractExpertRule> createOrCombination(List<AbstractExpertRule> rules) {
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rules.size() > 1 ? createCombination(CombinatorType.OR, rules) : rules.getFirst());
    }

    private AbstractExpertRule createVoltageLevelIdRule(UUID filterUuid, TwoSides side) {
        return FilterUuidExpertRule.builder()
            .operator(OperatorType.IS_PART_OF)
            .field(side == TwoSides.ONE ? FieldType.VOLTAGE_LEVEL_ID_1 : FieldType.VOLTAGE_LEVEL_ID_2)
            .values(Set.of(filterUuid.toString()))
            .build();
    }

    private ExpertFilter buildExpertFilterWithVoltageLevelIdsCriteria(UUID filterUuid, EquipmentType equipmentType) {
        AbstractExpertRule voltageLevelId1Rule = createVoltageLevelIdRule(filterUuid, TwoSides.ONE);
        AbstractExpertRule voltageLevelId2Rule = createVoltageLevelIdRule(filterUuid, TwoSides.TWO);
        AbstractExpertRule orCombination = createCombination(CombinatorType.OR, List.of(voltageLevelId1Rule, voltageLevelId2Rule));
        return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, orCombination);
    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            network.getVariantManager().setWorkingVariant(variantId);
            return network;
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public List<ResourceFilterDTO> getResourceFilters(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {

        Network network = getNetwork(networkUuid, variantId);

        final List<AbstractFilter> genericFilters = getFilters(globalFilter.getGenericFilter());

        EnumMap<EquipmentType, List<String>> subjectIdsByEquipmentType = new EnumMap<>(EquipmentType.class);
        for (EquipmentType equipmentType : getEquipmentTypes(globalFilter.getLimitViolationsTypes())) {
            List<List<String>> idsFilteredThroughEachFilter = new ArrayList<>();

            ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
            if (expertFilter != null) {
                idsFilteredThroughEachFilter.add(new ArrayList<>(filterNetwork(expertFilter, network, this)));
            }

            for (AbstractFilter filter : genericFilters) {
                if (filter.getEquipmentType() == equipmentType) {
                    idsFilteredThroughEachFilter.add(new ArrayList<>(filterNetwork(filter, network, this)));
                } else if (filter.getEquipmentType() == EquipmentType.VOLTAGE_LEVEL) {
                    ExpertFilter expertFilterWithVoltageLevelIdsCriteria = buildExpertFilterWithVoltageLevelIdsCriteria(filter.getId(), equipmentType);
                    idsFilteredThroughEachFilter.add(new ArrayList<>(filterNetwork(expertFilterWithVoltageLevelIdsCriteria, network, this)));
                }
            }

            if (idsFilteredThroughEachFilter.isEmpty()) {
                continue;
            }
            // combine the results
            // attention : generic filters all use AND operand between them while other filters use OR between them
            for (List<String> idsFiltered : idsFilteredThroughEachFilter) {
                // if there was already a filtered list for this equipment type : AND filtering :
                subjectIdsByEquipmentType.computeIfPresent(equipmentType, (key, value) -> value.stream()
                        .filter(idsFiltered::contains).toList());
                // otherwise, initialisation :
                subjectIdsByEquipmentType.computeIfAbsent(equipmentType, key -> new ArrayList<>(idsFiltered));
            }
        }

        // combine all the results into one list
        List<String> subjectIdsFromEvalFilter = new ArrayList<>();
        subjectIdsByEquipmentType.values().forEach(idsList ->
                Optional.ofNullable(idsList).ifPresent(subjectIdsFromEvalFilter::addAll)
        );

        return (subjectIdsFromEvalFilter.isEmpty()) ? List.of() :
            List.of(new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.IN, subjectIdsFromEvalFilter, Column.SUBJECT_ID.columnName()));
    }

    /**
     * @return list of the ids filtered from the network through the filter
     */
    @NotNull
    private static List<String> filterNetwork(AbstractFilter filter, Network network, FilterLoader filterLoader) {
        return FilterServiceUtils.getIdentifiableAttributes(filter, network, filterLoader)
                .stream()
                .map(IdentifiableAttributes::getId)
                .toList();
    }

    private Set<EquipmentType> getEquipmentTypes(List<LimitViolationType> violationTypes) {
        Set<EquipmentType> equipmentTypes = new HashSet<>();
        violationTypes.forEach(violationType -> equipmentTypes.addAll(
            switch (violationType) {
                case CURRENT -> List.of(EquipmentType.LINE, EquipmentType.TWO_WINDINGS_TRANSFORMER);
                case LOW_VOLTAGE, HIGH_VOLTAGE -> List.of(EquipmentType.VOLTAGE_LEVEL);
                default -> List.of();
            }
        ));
        return equipmentTypes;
    }
}
