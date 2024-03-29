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
import lombok.NonNull;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.AbstractExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.CombinatorExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.EnumExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.NumberExpertRule;
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
import java.util.stream.Collectors;

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

    public ExpertFilter buildExpertFilter(GlobalFilter globalFilter, EquipmentType equipmentType) {
        UUID filterId = UUID.randomUUID();
        Date modificationDate = new Date();
        List<AbstractExpertRule> andRules = new ArrayList<>();

        List<AbstractExpertRule> nominalVRules = new ArrayList<>();
        List<AbstractExpertRule> countryCodRules = new ArrayList<>();

        if (globalFilter != null) {
            if (globalFilter.getNominalV() != null) {
                nominalVRules.addAll(createNominalVoltageRules(globalFilter.getNominalV(), getNominalVoltageFieldType(equipmentType)));
            }
            if (globalFilter.getCountryCode() != null) {
                countryCodRules.addAll(createCountryCodeRules(globalFilter.getCountryCode(), getCountryCodeFieldType(equipmentType)));
            }

            if (globalFilter.getNominalV() != null && globalFilter.getCountryCode() != null) {
                if (!countryCodRules.isEmpty() && !nominalVRules.isEmpty()) {
                    if (nominalVRules.size() > 1) {
                        andRules.add(createOrCombinator(CombinatorType.OR, nominalVRules));
                    } else {
                        andRules.addAll(nominalVRules);
                    }
                }
                if (!countryCodRules.isEmpty()) {
                    if (countryCodRules.size() > 1) {
                        andRules.add(createOrCombinator(CombinatorType.OR, countryCodRules));
                    } else {
                        andRules.addAll(countryCodRules);
                    }
                }
                CombinatorExpertRule andCombination = CombinatorExpertRule.builder().combinator(CombinatorType.AND).rules(andRules).build();
                return new ExpertFilter(filterId, modificationDate, equipmentType, andCombination);
            } else {
                if (globalFilter.getNominalV() != null) {
                    return new ExpertFilter(filterId, modificationDate, equipmentType, createOrCombinator(CombinatorType.OR, nominalVRules));
                }
                if (globalFilter.getCountryCode() != null) {
                    return new ExpertFilter(filterId, modificationDate, equipmentType, createOrCombinator(CombinatorType.OR, countryCodRules));

                }
            }

        }
        return null;
    }

    private AbstractExpertRule createOrCombinator(CombinatorType combinatorType, List<AbstractExpertRule> rules) {
        return CombinatorExpertRule.builder().combinator(combinatorType).rules(rules).build();
    }

    private Network getNetwork(UUID networkUuid, PreloadingStrategy strategy, String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, strategy);
            if (variantId != null) {
                network.getVariantManager().setWorkingVariant(variantId);
            }
            return network;
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public List<ResourceFilter> getResourceFilters(@NonNull UUID networkUuid, String variantId, @NonNull GlobalFilter globalFilter) {
        List<ResourceFilter> resourceFilters = new ArrayList<>();
        List<EquipmentType> equipmentTypes = new ArrayList<>();
        List<String> subjectIdsFromEvalFilter = new ArrayList<>();

        Network network = getNetwork(networkUuid, PreloadingStrategy.COLLECTION, variantId);

        if (globalFilter.getLimitViolationsType().equals(GlobalFilter.LimitViolationsType.CURRENT.name())) {
            equipmentTypes = List.of(EquipmentType.LINE, EquipmentType.TWO_WINDINGS_TRANSFORMER);
        }
        if (globalFilter.getLimitViolationsType().equals(GlobalFilter.LimitViolationsType.VOLTAGE.name())) {
            equipmentTypes = List.of(EquipmentType.VOLTAGE_LEVEL);
        }

        for (EquipmentType equipmentType : equipmentTypes) {
            ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
            if (expertFilter != null) {
                subjectIdsFromEvalFilter.addAll(FilterServiceUtils.getIdentifiableAttributes(expertFilter, network, null).stream().map(e -> e.getId()).collect(Collectors.toList()));
            }
        }

        if (!subjectIdsFromEvalFilter.isEmpty()) {
            resourceFilters.add(new ResourceFilter(ResourceFilter.DataType.TEXT, ResourceFilter.Type.IN, subjectIdsFromEvalFilter, ResourceFilter.Column.SUBJECT_ID));
        } else {
            return List.of();
        }
        return resourceFilters;
    }

}
