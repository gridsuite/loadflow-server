package org.gridsuite.loadflow.server.service.filters;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.expertfilter.expertrule.*;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.expertfilter.CombinatorType;
import org.gridsuite.filter.utils.expertfilter.FieldType;
import org.gridsuite.filter.utils.expertfilter.OperatorType;
import org.gridsuite.loadflow.server.dto.GlobalFilter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@Service
public class FilterService {
    public ExpertFilter buildExpertFilter(GlobalFilter globalFilter, EquipmentType equipmentType) {
        ExpertFilter expertFilter = new ExpertFilter();
        expertFilter.setEquipmentType(equipmentType);

        CombinatorExpertRule combinatorExpertRule = buildCombinedRules(globalFilter, equipmentType);
        expertFilter.setRules(combinatorExpertRule);

        return expertFilter;
    }

    private CombinatorExpertRule buildCombinedRules(GlobalFilter globalFilter, EquipmentType equipmentType) {
        List<AbstractExpertRule> rules = new ArrayList<>();

        // build nominal voltage rules
        if (globalFilter.getNominalV() != null) {
            rules.add(buildNominalVoltageRules(globalFilter.getNominalV(), equipmentType));
        }

        // build country code rules
        if (globalFilter.getCountryCode() != null) {
            rules.add(buildCountryCodeRules(globalFilter.getCountryCode(), equipmentType));
        }

        // Combine rules with AND operator
        return CombinatorExpertRule.builder()
                .combinator(CombinatorType.AND)
                .rules(rules)
                .build();
    }

    private CombinatorExpertRule buildNominalVoltageRules(List<String> nominalVoltages, EquipmentType equipmentType) {
        List<AbstractExpertRule> voltageRules = nominalVoltages.stream()
                .map(voltage -> buildNominalVoltageRule(equipmentType, voltage))
                .collect(Collectors.toList());

        return CombinatorExpertRule.builder()
                .combinator(CombinatorType.OR)
                .rules(voltageRules)
                .build();
    }

    private AbstractExpertRule buildNominalVoltageRule(EquipmentType equipmentType, String voltage) {
        double voltageValue = Double.parseDouble(voltage);
        switch (equipmentType) {
            case VOLTAGE_LEVEL:
                return NumberExpertRule.builder()
                        .value(voltageValue)
                        .field(FieldType.NOMINAL_VOLTAGE)
                        .operator(OperatorType.EQUALS)
                        .build();
            case LINE:
            case TWO_WINDINGS_TRANSFORMER:
                return CombinatorExpertRule.builder()
                        .combinator(CombinatorType.OR)
                        .rules(Arrays.asList(
                                NumberExpertRule.builder()
                                        .value(voltageValue)
                                        .field(FieldType.NOMINAL_VOLTAGE_1)
                                        .operator(OperatorType.EQUALS)
                                        .build(),
                                NumberExpertRule.builder()
                                        .value(voltageValue)
                                        .field(FieldType.NOMINAL_VOLTAGE_2)
                                        .operator(OperatorType.EQUALS)
                                        .build()))
                        .build();
            default:
                throw new IllegalArgumentException("Unsupported equipment type: " + equipmentType);
        }
    }

    private CombinatorExpertRule buildCountryCodeRules(List<String> countryCodes, EquipmentType equipmentType) {
        List<AbstractExpertRule> countryRules = new ArrayList<>();
        countryRules.add(EnumExpertRule.builder()
                .values(new HashSet<>(countryCodes))
                .field(equipmentType == EquipmentType.LINE ? FieldType.COUNTRY_1 : FieldType.COUNTRY)
                .operator(OperatorType.IN)
                .build());

        if (equipmentType == EquipmentType.LINE) {
            countryRules.add(EnumExpertRule.builder()
                    .values(new HashSet<>(countryCodes))
                    .field(FieldType.COUNTRY_2)
                    .operator(OperatorType.IN)
                    .build());
        }

        return CombinatorExpertRule.builder()
                .combinator(CombinatorType.OR)
                .rules(countryRules)
                .build();
    }
}
