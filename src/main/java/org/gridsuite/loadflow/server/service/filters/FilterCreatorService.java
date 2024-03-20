package org.gridsuite.loadflow.server.service.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gridsuite.loadflow.server.dto.GlobalFilter;
import org.gridsuite.loadflow.server.utils.LoadflowException;
import org.springframework.stereotype.Service;

//TODO: to delete after merging filter library
@Service
public class FilterCreatorService {
    public String createExpertFilterFromGlobalFilter(GlobalFilter globalFilter, String equipmentType) {
        String jsonString = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode rootNode = objectMapper.createObjectNode();
            ArrayNode rulesArrayNode = objectMapper.createArrayNode();
            if (equipmentType != null && globalFilter != null ) {
                // Create combined filter for NOMINAL_VOLTAGE combinator
                ObjectNode nominalVoltageCombinatorNode = createCombinatorNode("OR", objectMapper);
                if (globalFilter.getNominalV() != null) {
                    for (String nominalVoltage : globalFilter.getNominalV()) {
                        if (equipmentType.equals("VOLTAGE_LEVEL")) {
                            nominalVoltageCombinatorNode.withArray("rules").add(createRuleNode(nominalVoltage, "NOMINAL_VOLTAGE", "NUMBER", objectMapper));
                        } else if (equipmentType.equals("LINE")) {
                            nominalVoltageCombinatorNode.withArray("rules").add(createRuleNode(nominalVoltage, "NOMINAL_VOLTAGE_1", "NUMBER", objectMapper));
                            nominalVoltageCombinatorNode.withArray("rules").add(createRuleNode(nominalVoltage, "NOMINAL_VOLTAGE_2", "NUMBER", objectMapper));

                        } else if (equipmentType.equals("TWO_WINDINGS_TRANSFORMER")) {
                            nominalVoltageCombinatorNode.withArray("rules").add(createRuleNode(nominalVoltage, "NOMINAL_VOLTAGE_1", "NUMBER", objectMapper));
                            nominalVoltageCombinatorNode.withArray("rules").add(createRuleNode(nominalVoltage, "NOMINAL_VOLTAGE_2", "NUMBER", objectMapper));

                        }
                    }
                    rulesArrayNode.add(nominalVoltageCombinatorNode);
                }

                // Create combined filter for COUNTRY combinator
                ObjectNode countryCodeCombinatorNode = createCombinatorNode("OR", objectMapper);
                if (globalFilter.getCountryCode() != null) {
                    for (String countryCode : globalFilter.getCountryCode()) {
                        if (equipmentType.equals("VOLTAGE_LEVEL")) {
                            countryCodeCombinatorNode.withArray("rules").add(createRuleNode(countryCode, "COUNTRY", "ENUM", objectMapper));
                        } else if (equipmentType.equals("LINE")) {
                            countryCodeCombinatorNode.withArray("rules").add(createRuleNode(countryCode, "COUNTRY_1", "ENUM", objectMapper));
                            countryCodeCombinatorNode.withArray("rules").add(createRuleNode(countryCode, "COUNTRY_2", "ENUM", objectMapper));

                        } else if (equipmentType.equals("TWO_WINDINGS_TRANSFORMER")) {
                            countryCodeCombinatorNode.withArray("rules").add(createRuleNode(countryCode, "COUNTRY", "ENUM", objectMapper));
                        }
                    }
                    rulesArrayNode.add(countryCodeCombinatorNode);
                }
            }

            // Create top-level combinator node for AND logic
            ObjectNode topLevelCombinatorNode = createCombinatorNode("AND", objectMapper);
            topLevelCombinatorNode.set("rules", rulesArrayNode);

            rootNode.put("type", "EXPERT");
            rootNode.put("equipmentType", equipmentType);
            rootNode.set("rules", topLevelCombinatorNode);

            jsonString = objectMapper.writeValueAsString(rootNode);
        } catch (JsonProcessingException e) {
            throw new LoadflowException(LoadflowException.Type.INVALID_FILTER_FORMAT);
        }
        return jsonString;
    }

    public ObjectNode createCombinatorNode(String combinatorType, ObjectMapper objectMapper) {
        ObjectNode combinatorNode = objectMapper.createObjectNode();
        combinatorNode.put("combinator", combinatorType);
        combinatorNode.put("dataType", "COMBINATOR");
        return combinatorNode;
    }

    public ObjectNode createRuleNode(String value, String criteria, String dataType, ObjectMapper objectMapper) {
        ObjectNode ruleNode = objectMapper.createObjectNode();
        ruleNode.put("field", criteria);
        ruleNode.put("operator", "EQUALS");
        ruleNode.put("value", value);
        ruleNode.put("dataType", dataType);
        return ruleNode;
    }
}
