package org.gridsuite.loadflow.server.service.filters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.gridsuite.loadflow.server.dto.GlobalFilter;
import org.gridsuite.loadflow.server.utils.LoadflowException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FilterCreatorService {
    public String createFilterFromGlobalFilter(List<GlobalFilter> globalFilters, String equipmentType){
        String jsonString = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode rootNode = objectMapper.createObjectNode();

            // Create a rules node
            ObjectNode rulesNode = objectMapper.createObjectNode();

            // Create rules array node
            ArrayNode rulesArrayNode = objectMapper.createArrayNode();

            if (equipmentType.equals("VOLTAGE_LEVEL")) {
                for (GlobalFilter globalFilter : globalFilters) {
                    // Create first rule node
                    ObjectNode firstRuleNode = createRuleNode(globalFilter.getNominalV(),"NOMINAL_VOLTAGE","NUMBER",objectMapper);
                    // Create second rule node
                    ObjectNode secondRuleNode = createRuleNode(globalFilter.getCountryCode(),"COUNTRY","ENUM",objectMapper);
                    // Create combinator node for each filter
                    ObjectNode filterNode = objectMapper.createObjectNode();
                    filterNode.put("combinator", "AND");
                    filterNode.put("dataType", "COMBINATOR");
                    // Add first and second rule nodes to rules array
                    ArrayNode filterRulesArrayNode = objectMapper.createArrayNode();
                    filterRulesArrayNode.add(firstRuleNode);
                    filterRulesArrayNode.add(secondRuleNode);
                    // Add rules array to filter node
                    filterNode.set("rules", filterRulesArrayNode);
                    // Add each filter node to rules array
                    rulesArrayNode.add(filterNode);
                }
            }
            if (equipmentType.equals("LINE")) {
                for (GlobalFilter globalFilter : globalFilters) {
                    // Create first rule node for NOMINAL_VOLTAGE_1
                    ObjectNode nominalVoltage1RuleNode = createRuleNode(globalFilter.getNominalV(),"NOMINAL_VOLTAGE_1","NUMBER",objectMapper);
                    // Create second rule node for NOMINAL_VOLTAGE_2
                    ObjectNode nominalVoltage2RuleNode = createRuleNode(globalFilter.getNominalV(),"NOMINAL_VOLTAGE_2","NUMBER",objectMapper);
                    // Create first rule node for COUNTRY_1
                    ObjectNode country1RuleNode = createRuleNode(globalFilter.getCountryCode(),"COUNTRY_1","ENUM",objectMapper);
                    // Create second rule node for COUNTRY_2
                    ObjectNode country2RuleNode = createRuleNode(globalFilter.getCountryCode(),"COUNTRY_2","ENUM",objectMapper);
                    // Create combinator node for NOMINAL_VOLTAGE rules
                    ObjectNode nominalVoltageCombinatorNode = createCombinatorNode(objectMapper.createArrayNode().add(nominalVoltage1RuleNode).add(nominalVoltage2RuleNode),"OR",objectMapper);
                    // Create combinator node for COUNTRY rules
                    ObjectNode countryCombinatorNode =createCombinatorNode( objectMapper.createArrayNode().add(country1RuleNode).add(country2RuleNode),"OR",objectMapper);
                    // Create top-level combinator node
                    ObjectNode topLevelCombinatorNode = createCombinatorNode(objectMapper.createArrayNode().add(nominalVoltageCombinatorNode).add(countryCombinatorNode),"AND",objectMapper);
                    // Add top-level combinator node to rules array
                    rulesArrayNode.add(topLevelCombinatorNode);
                }
            }
            if (equipmentType.equals("TWO_WINDINGS_TRANSFORMER")) {
                for (GlobalFilter globalFilter : globalFilters) {
                    // Create first rule node for NOMINAL_VOLTAGE_1
                    ObjectNode voltage1RuleNode = createRuleNode(globalFilter.getNominalV(),"NOMINAL_VOLTAGE_1","NUMBER",objectMapper);
                    // Create second rule node for NOMINAL_VOLTAGE_2
                    ObjectNode voltage2RuleNode = createRuleNode(globalFilter.getNominalV(),"NOMINAL_VOLTAGE_2","NUMBER",objectMapper);

                    // Add NOMINAL_VOLTAGE rules to the combinator
                    ArrayNode voltageRulesArrayNode = objectMapper.createArrayNode();
                    voltageRulesArrayNode.add(voltage1RuleNode);
                    voltageRulesArrayNode.add(voltage2RuleNode);
                    // Create OR combinator for NOMINAL_VOLTAGE rules
                    ObjectNode voltageCombinatorNode = objectMapper.createObjectNode();
                    voltageCombinatorNode.put("combinator", "OR");
                    voltageCombinatorNode.put("dataType", "COMBINATOR");
                    voltageCombinatorNode.set("rules", voltageRulesArrayNode);

                    // Create rule node for COUNTRY
                    ObjectNode countryRuleNode = createRuleNode(globalFilter.getCountryCode(),"COUNTRY","ENUM",objectMapper);

                    // Create OR combinator for NOMINAL_VOLTAGE and COUNTRY rules
                    ObjectNode mainCombinatorNode = objectMapper.createObjectNode();
                    mainCombinatorNode.put("combinator", "OR");
                    mainCombinatorNode.put("dataType", "COMBINATOR");

                    // Add NOMINAL_VOLTAGE combinator and COUNTRY rule to main combinator
                    ArrayNode mainRulesArrayNode = objectMapper.createArrayNode();
                    mainRulesArrayNode.add(voltageCombinatorNode);
                    mainRulesArrayNode.add(countryRuleNode);
                    mainCombinatorNode.set("rules", mainRulesArrayNode);

                    // Add main combinator to rules array
                    rulesArrayNode.add(mainCombinatorNode);
                }
            }

            // Add rules array to rules node
            rulesNode.put("combinator", "OR");
            rulesNode.put("dataType", "COMBINATOR");
            rulesNode.set("rules", rulesArrayNode);

            // Add type, equipmentType, and rules node to the root node
            rootNode.put("type", "EXPERT");
            rootNode.put("equipmentType", equipmentType);
            rootNode.set("rules", rulesNode);

            // Convert root node to JSON string
            jsonString = objectMapper.writeValueAsString(rootNode);
        } catch (JsonProcessingException e) {
            throw new LoadflowException(LoadflowException.Type.INVALID_FILTER_FORMAT);
        }
        return jsonString;
    }
    public ObjectNode createCombinatorNode(JsonNode rulesNode, String combinator,ObjectMapper objectMapper){
        ObjectNode combinatorNode = objectMapper.createObjectNode();
        combinatorNode.put("combinator", combinator);
        combinatorNode.put("dataType", "COMBINATOR");
        combinatorNode.set("rules", rulesNode);
        return combinatorNode;
    }
    public ObjectNode createRuleNode(String value, String criteria, String dataType, ObjectMapper objectMapper){
        ObjectNode ruleNode = objectMapper.createObjectNode();
        ruleNode.put("field", criteria);
        ruleNode.put("operator", "EQUALS");
        ruleNode.put("value", value);
        ruleNode.put("dataType", dataType);
        return ruleNode;
    }
}
