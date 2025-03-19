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
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.security.LimitViolationType;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.AbstractExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.CombinatorExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.EnumExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.NumberExpertRule;
import org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.FilterServiceUtils;
import org.gridsuite.filter.utils.expertfilter.CombinatorType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.gridsuite.loadflow.server.dto.GlobalFilter;
import org.gridsuite.loadflow.server.dto.ResourceFilter;
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
public class FilterService {
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
        var ids = !filtersUuids.isEmpty() ? "?ids=" + filtersUuids.stream().map(UUID::toString).collect(Collectors.joining(",")) : "";
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
        for (String value : values) {
            rules.add(NumberExpertRule.builder()
                    .value(Double.valueOf(value))
                    .field(fieldType)
                    .operator(OperatorType.EQUALS)
                    .build());
        }
        return rules;
    }

    private List<AbstractExpertRule> createEnumExpertRules(List<Country> values, FieldType fieldType) {
        List<AbstractExpertRule> rules = new ArrayList<>();
        for (Country value : values) {
            rules.add(EnumExpertRule.builder()
                    .value(value.toString())
                    .field(fieldType)
                    .operator(OperatorType.EQUALS)
                    .build());
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

    private ExpertFilter buildExpertFilter(GlobalFilter globalFilter, EquipmentType equipmentType) {
        List<AbstractExpertRule> nominalVRules = List.of();
        if (globalFilter.getNominalV() != null) {
            nominalVRules = createNominalVoltageRules(globalFilter.getNominalV(), getNominalVoltageFieldType(equipmentType));
        }

        List<AbstractExpertRule> countryCodRules = List.of();
        if (globalFilter.getCountryCode() != null) {
            countryCodRules = createCountryCodeRules(globalFilter.getCountryCode(), getCountryCodeFieldType(equipmentType));
        }

        if (nominalVRules.isEmpty() && countryCodRules.isEmpty()) {
            return null;
        }

        if (countryCodRules.isEmpty()) {
            return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, createOrCombinator(CombinatorType.OR, nominalVRules));
        }

        if (nominalVRules.isEmpty()) {
            return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, createOrCombinator(CombinatorType.OR, countryCodRules));
        }

        List<AbstractExpertRule> andRules = new ArrayList<>();
        andRules.addAll(nominalVRules.size() > 1 ? List.of(createOrCombinator(CombinatorType.OR, nominalVRules)) : nominalVRules);
        andRules.addAll(countryCodRules.size() > 1 ? List.of(createOrCombinator(CombinatorType.OR, countryCodRules)) : countryCodRules);
        AbstractExpertRule andCombination = createOrCombinator(CombinatorType.AND, andRules);

        return new ExpertFilter(UUID.randomUUID(), new Date(), equipmentType, andCombination);
    }

    private AbstractExpertRule createOrCombinator(CombinatorType combinatorType, List<AbstractExpertRule> rules) {
        return CombinatorExpertRule.builder().combinator(combinatorType).rules(rules).build();
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

    public List<ResourceFilter> getResourceFilters(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {

        Network network = getNetwork(networkUuid, variantId);

        final List<AbstractFilter> genericFilters = getFilters(globalFilter.getGenericFilter());

        EnumMap<EquipmentType, List<String>> subjectIdsByEquipmentType = new EnumMap<>(EquipmentType.class);
        for (EquipmentType equipmentType : getEquipmentTypes(globalFilter.getLimitViolationsTypes())) {
            List<List<String>> idsFilteredThroughEachFilter = new ArrayList<>();

            ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
            if (expertFilter != null) {
                idsFilteredThroughEachFilter.add(new ArrayList<>(filterNetwork(expertFilter, network)));
            }

            for (AbstractFilter filter : genericFilters) {
                if (filter.getEquipmentType() == equipmentType) {
                    idsFilteredThroughEachFilter.add(new ArrayList<>(filterNetwork(filter, network)));
                }
            }

            // combine the results
            // attention : generic filters all use AND operand between them while other filters use OR between them
            if (!idsFilteredThroughEachFilter.isEmpty()) {
                for (List<String> idsFiltered : idsFilteredThroughEachFilter) {
                    // if there was already a filtered list for this equipment type : AND filtering :
                    subjectIdsByEquipmentType.computeIfPresent(equipmentType, (key, value) -> value.stream()
                            .filter(idsFiltered::contains).toList());
                    // otherwise, initialisation :
                    subjectIdsByEquipmentType.computeIfAbsent(equipmentType, key -> new ArrayList<>(idsFiltered));
                }
            }
        }

        // combine all the results into one list
        List<String> subjectIdsFromEvalFilter = new ArrayList<>();
        subjectIdsByEquipmentType.values().forEach(idsList ->
                Optional.ofNullable(idsList).ifPresent(subjectIdsFromEvalFilter::addAll)
        );

        return (subjectIdsFromEvalFilter.isEmpty()) ? List.of() :
            List.of(new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.IN, subjectIdsFromEvalFilter, ResourceFilter.Column.SUBJECT_ID));
    }

    /**
     * @return list of the ids filtered from the network through the filter
     */
    @NotNull
    private static List<String> filterNetwork(AbstractFilter filter, Network network) {
        return FilterServiceUtils.getIdentifiableAttributes(filter, network, null)
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
