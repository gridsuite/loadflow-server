/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service.filters;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.security.LimitViolationType;
import lombok.NonNull;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@Service
public class FilterService {
    private final NetworkStoreService networkStoreService;

    public FilterService(
            NetworkStoreService networkStoreService) {
        this.networkStoreService = networkStoreService;
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

        List<String> subjectIdsFromEvalFilter = List.of();
        for (EquipmentType equipmentType : getEquipmentTypes(globalFilter.getLimitViolationsTypes())) {
            ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
            if (expertFilter != null) {
                subjectIdsFromEvalFilter = FilterServiceUtils.getIdentifiableAttributes(expertFilter, network, null).stream().map(IdentifiableAttributes::getId).toList();
            }
        }

        return (!subjectIdsFromEvalFilter.isEmpty()) ?
            List.of(new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.IN, subjectIdsFromEvalFilter, ResourceFilter.Column.SUBJECT_ID)) : List.of();
    }

    private List<EquipmentType> getEquipmentTypes(List<LimitViolationType> violationTypes) {
        List<EquipmentType> equipmentTypes = new ArrayList<>();
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
