/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.security.LimitViolationType;
import lombok.NonNull;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.AbstractFilterService;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.loadflow.server.dto.Column;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@Service
public class FilterService extends AbstractFilterService {

    public FilterService(RestTemplateBuilder restTemplateBuilder,
                         NetworkStoreService networkStoreService,
                         @Value("${gridsuite.services.filter-server.base-uri:http://filter-server/}") String filterServerBaseUri) {
        super(restTemplateBuilder, networkStoreService, filterServerBaseUri);
    }

    public Optional<ResourceFilterDTO> getResourceFilter(@NonNull UUID networkUuid, @NonNull String variantId, @NonNull GlobalFilter globalFilter) {
        // Get equipment types from violation types
        List<EquipmentType> equipmentTypes = getEquipmentTypes(globalFilter.getLimitViolationsTypes());

        // Call the common implementation with specific parameters
        return super.getResourceFilter(networkUuid, variantId, globalFilter, equipmentTypes, Column.SUBJECT_ID.columnName());
    }

    /**
     * Gets equipment types based on limit violation types
     */
    private List<EquipmentType> getEquipmentTypes(List<LimitViolationType> violationTypes) {
        return violationTypes.stream()
                .flatMap(violationType -> switch (violationType) {
                    case CURRENT -> Stream.of(EquipmentType.LINE, EquipmentType.TWO_WINDINGS_TRANSFORMER);
                    case LOW_VOLTAGE, HIGH_VOLTAGE -> Stream.of(EquipmentType.VOLTAGE_LEVEL);
                    default -> Stream.empty();
                })
                .distinct()
                .toList();
    }
}
