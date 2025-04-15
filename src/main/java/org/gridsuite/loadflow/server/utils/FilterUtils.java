/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.loadflow.server.dto.GlobalFilter;

import java.util.List;

/**
 * @author maissa Souissi <maissa.souissi at rte-france.com>
 */
public final class FilterUtils { // TODO à déplacer vers ws-commons ??

    // Utility class, so no constructor
    private FilterUtils() {
    }

    private static <T> T fromStringToDTO(String jsonString, ObjectMapper objectMapper, TypeReference<T> typeReference, T defaultValue) {
        if (StringUtils.isEmpty(jsonString)) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(jsonString, typeReference);
        } catch (JsonProcessingException e) {
            throw new LoadflowException(LoadflowException.Type.INVALID_FILTER_FORMAT);
        }
    }

    public static List<ResourceFilterDTO> fromStringFiltersToDTO(String stringFilters, ObjectMapper objectMapper) {
        return fromStringToDTO(stringFilters, objectMapper, new TypeReference<>() {
        }, List.of());
    }

    public static GlobalFilter fromStringGlobalFiltersToDTO(String stringGlobalFilters, ObjectMapper objectMapper) {
        return fromStringToDTO(stringGlobalFilters, objectMapper, new TypeReference<>() {
        }, null);
    }
}
