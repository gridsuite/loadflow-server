package org.gridsuite.loadflow.server.service.filters;

import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.AbstractExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.CombinatorExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.EnumExpertRule;
import org.gridsuite.filter.expertfilter.expertrule.NumberExpertRule;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.expertfilter.CombinatorType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.gridsuite.loadflow.server.dto.GlobalFilter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@Service
public class FilterService {
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

    private List<AbstractExpertRule> createEnumExpertRules(List<String> values, FieldType fieldType) {
        List<AbstractExpertRule> rules = new ArrayList<>();
        for (String value : values) {
            rules.add(EnumExpertRule.builder()
                    .value(value)
                    .field(fieldType)
                    .operator(OperatorType.EQUALS)
                    .build());
        }
        return rules;
    }

    List<AbstractExpertRule> createNominalVoltageRules(List<String> nominalVoltageList, List<FieldType> nominalFieldTypes) {
        List<AbstractExpertRule> nominalVoltageRules = new ArrayList<>();
        for (FieldType fieldType : nominalFieldTypes) {
            nominalVoltageRules.addAll(createNumberExpertRules(nominalVoltageList, fieldType));
        }
        return nominalVoltageRules;
    }

    List<AbstractExpertRule> createCountryCodeRules(List<String> countryCodeList, List<FieldType> countryCodeFieldTypes) {
        List<AbstractExpertRule> countryCodeRules = new ArrayList<>();
        for (FieldType fieldType : countryCodeFieldTypes) {
            countryCodeRules.addAll(createEnumExpertRules(countryCodeList, fieldType));
        }
        return countryCodeRules;
    }

    List<FieldType> getNominalVoltageFieldType(EquipmentType equipmentType) {
        boolean isLineOrTwoWT = equipmentType.equals(EquipmentType.LINE) || equipmentType.equals(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        if (isLineOrTwoWT) {
            return List.of(FieldType.NOMINAL_VOLTAGE_1, FieldType.NOMINAL_VOLTAGE_2);
        }
        if (equipmentType.equals(EquipmentType.VOLTAGE_LEVEL)) {
            return List.of(FieldType.NOMINAL_VOLTAGE);
        }
        return List.of();
    }

    List<FieldType> getCountryCodeFieldType(EquipmentType equipmentType) {
        boolean isLVoltageLevelOrTwoWT = equipmentType.equals(EquipmentType.VOLTAGE_LEVEL) || equipmentType.equals(EquipmentType.TWO_WINDINGS_TRANSFORMER);
        if (isLVoltageLevelOrTwoWT) {
            return List.of(FieldType.COUNTRY);
        }
        if (equipmentType.equals(EquipmentType.VOLTAGE_LEVEL)) {
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

}
