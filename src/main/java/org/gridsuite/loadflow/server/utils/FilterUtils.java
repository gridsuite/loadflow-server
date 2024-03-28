package org.gridsuite.loadflow.server.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.loadflow.server.dto.GlobalFilter;
import org.gridsuite.loadflow.server.dto.ResourceFilter;

import java.util.List;

/**
 * @author maissa Souissi <maissa.souissi at rte-france.com>
 */
public final class FilterUtils {

    // Utility class, so no constructor
    private FilterUtils() {
    }

    public static List<ResourceFilter> fromStringFiltersToDTO(String stringFilters, ObjectMapper objectMapper) {
        if (StringUtils.isEmpty(stringFilters)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(stringFilters, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new LoadflowException(LoadflowException.Type.INVALID_FILTER_FORMAT);
        }
    }

    public static GlobalFilter fromStringGlobalFiltersToDTO(String stringGlobalFilters, ObjectMapper objectMapper) {
        if (StringUtils.isEmpty(stringGlobalFilters)) {
            return null;
        }
        try {
            return objectMapper.readValue(stringGlobalFilters, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new LoadflowException(LoadflowException.Type.INVALID_FILTER_FORMAT);
        }
    }
}
