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
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.loadflow.server.dto.Column;
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

    public Optional<ResourceFilterDTO> getResourceFilter(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
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

        return subjectIdsFromEvalFilter.isEmpty() ? Optional.empty() :
                Optional.of(new ResourceFilterDTO(
                        ResourceFilterDTO.DataType.TEXT,
                        ResourceFilterDTO.Type.IN,
                        subjectIdsFromEvalFilter,
                        Column.SUBJECT_ID.columnName()
                ));
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
