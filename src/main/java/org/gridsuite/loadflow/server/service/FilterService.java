/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.security.LimitViolationType;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.service.AbstractFilterService;
import lombok.NonNull;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.FilterLoader;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.AbstractExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.FilterUuidExpertRule;
import org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.FilterServiceUtils;
import org.gridsuite.filter.utils.expertfilter.CombinatorType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.gridsuite.loadflow.server.dto.Column;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@Service
public class FilterService extends AbstractFilterService {

    public FilterService(
            NetworkStoreService networkStoreService,
            @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        super(networkStoreService, filterServerBaseUri);
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

    protected List<FieldType> getNominalVoltageFieldType(EquipmentType equipmentType) {
        boolean isLineOrTwoWT = equipmentType.equals(EquipmentType.LINE) || equipmentType.equals(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        if (isLineOrTwoWT) {
            return List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2);
        }
        if (equipmentType.equals(EquipmentType.VOLTAGE_LEVEL)) {
            return List.of(FieldType.NOMINAL_VOLTAGE);
        }
        return List.of();
    }

    protected List<FieldType> getCountryCodeFieldType(EquipmentType equipmentType) {
        boolean isLVoltageLevelOrTwoWT = equipmentType.equals(EquipmentType.VOLTAGE_LEVEL) || equipmentType.equals(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        if (isLVoltageLevelOrTwoWT) {
            return List.of(FieldType.COUNTRY);
        }
        if (equipmentType.equals(EquipmentType.LINE)) {
            return List.of(FieldType.COUNTRY_1, FieldType.COUNTRY_2);

        }
        return List.of();
    }

    protected List<FieldType> getSubstationPropertiesFieldTypes(EquipmentType equipmentType) {
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
