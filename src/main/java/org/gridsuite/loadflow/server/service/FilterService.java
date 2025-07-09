/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.security.LimitViolationType;
import com.powsybl.ws.commons.computation.dto.GlobalFilter;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.service.AbstractFilterService;
import lombok.NonNull;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.FilterLoader;
import org.gridsuite.filter.expertfilter.ExpertFilter;
import org.gridsuite.filter.identifierlistfilter.IdentifiableAttributes;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.filter.utils.FilterServiceUtils;
import org.gridsuite.filter.utils.expertfilter.FieldType;
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

    @Override
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

    @Override
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

    @Override
    protected List<FieldType> getSubstationPropertiesFieldTypes(EquipmentType equipmentType) {
        if (equipmentType.equals(EquipmentType.LINE)) {
            return List.of(FieldType.SUBSTATION_PROPERTIES_1, FieldType.SUBSTATION_PROPERTIES_2);
        }
        return List.of(FieldType.SUBSTATION_PROPERTIES);
    }

    public List<ResourceFilterDTO> getResourceFilters(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
        Network network = getNetwork(networkUuid, variantId);
        List<AbstractFilter> genericFilters = getFilters(globalFilter.getGenericFilter());

        // Get equipment types from violation types
        Set<EquipmentType> equipmentTypes = getEquipmentTypes(globalFilter.getLimitViolationsTypes());

        // Filter equipments by type
        Map<EquipmentType, List<String>> subjectIdsByEquipmentType = filterEquipmentsByType(
                network, globalFilter, genericFilters, new ArrayList<>(equipmentTypes)
        );

        // Combine all results into one list
        List<String> subjectIdsFromEvalFilter = subjectIdsByEquipmentType.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();

        return subjectIdsFromEvalFilter.isEmpty() ? List.of() :
                List.of(new ResourceFilterDTO(
                        ResourceFilterDTO.DataType.TEXT,
                        ResourceFilterDTO.Type.IN,
                        subjectIdsFromEvalFilter,
                        Column.SUBJECT_ID.columnName()
                ));
    }

    /**
     * Filters equipments by type and returns map of IDs grouped by equipment type
     */
    private Map<EquipmentType, List<String>> filterEquipmentsByType(
            Network network,
            GlobalFilter globalFilter,
            List<AbstractFilter> genericFilters,
            List<EquipmentType> equipmentTypes) {

        EnumMap<EquipmentType, List<String>> result = new EnumMap<>(EquipmentType.class);

        for (EquipmentType equipmentType : equipmentTypes) {
            List<String> filteredIds = extractFilteredEquipmentIds(network, globalFilter, genericFilters, equipmentType);
            if (!filteredIds.isEmpty()) {
                result.put(equipmentType, filteredIds);
            }
        }

        return result;
    }

    /**
     * Extracts filtered equipment IDs by applying expert and generic filters
     */
    private List<String> extractFilteredEquipmentIds(
            Network network,
            GlobalFilter globalFilter,
            List<AbstractFilter> genericFilters,
            EquipmentType equipmentType) {

        List<List<String>> idsFilteredThroughEachFilter = new ArrayList<>();

        // Extract IDs from expert filter
        ExpertFilter expertFilter = buildExpertFilter(globalFilter, equipmentType);
        if (expertFilter != null) {
            idsFilteredThroughEachFilter.add(filterNetwork(expertFilter, network, this));
        }

        // Extract IDs from generic filters
        for (AbstractFilter filter : genericFilters) {
            List<String> filterResult = extractEquipmentIdsFromGenericFilter(filter, equipmentType, network);
            if (!filterResult.isEmpty()) {
                idsFilteredThroughEachFilter.add(filterResult);
            }
        }

        if (idsFilteredThroughEachFilter.isEmpty()) {
            return List.of();
        }

        // Apply the specific combination logic for this implementation
        // Generic filters use AND between them
        return applyFilterCombinationLogic(idsFilteredThroughEachFilter);
    }

    /**
     * Applies the specific filter combination logic
     * Note: This implementation uses AND logic between all filters
     */
    private List<String> applyFilterCombinationLogic(List<List<String>> idsFilteredThroughEachFilter) {
        if (idsFilteredThroughEachFilter.isEmpty()) {
            return List.of();
        }

        // Start with the first list
        List<String> result = new ArrayList<>(idsFilteredThroughEachFilter.getFirst());

        // Apply AND operation with each subsequent list
        for (int i = 1; i < idsFilteredThroughEachFilter.size(); i++) {
            List<String> currentList = idsFilteredThroughEachFilter.get(i);
            result = result.stream()
                    .filter(currentList::contains)
                    .toList();
        }

        return result;
    }

    /**
     * Static method to filter network - kept for compatibility
     * @return list of the ids filtered from the network through the filter
     */
    @NotNull
    private static List<String> filterNetwork(AbstractFilter filter, Network network, FilterLoader filterLoader) {
        return FilterServiceUtils.getIdentifiableAttributes(filter, network, filterLoader)
                .stream()
                .map(IdentifiableAttributes::getId)
                .toList();
    }

    /**
     * Override to use the static filterNetwork method with FilterLoader
     */
    @Override
    protected List<String> filterNetwork(AbstractFilter filter, Network network) {
        return filterNetwork(filter, network, this);
    }

    /**
     * Gets equipment types based on limit violation types
     */
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
