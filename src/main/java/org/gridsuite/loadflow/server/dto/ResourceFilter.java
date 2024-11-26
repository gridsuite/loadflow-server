/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An object that can be used to filter data with the JPA Criteria API (via Spring Specification)
 * @param dataType the type of data we want to filter (text, number)
 * @param type the type of filter (contains, startsWith...)
 * @param value the value of the filter
 * @param column the column on which the filter will be applied
 * @param tolerance precision/tolerance used for the comparisons (simulates the rounding of the database values) Only useful for numbers.
 * @author Anis TOURI <anis.touri@rte-france.com>
 */
public record ResourceFilter(DataType dataType, Type type, Object value, Column column, Double tolerance) {
    public ResourceFilter(DataType dataType, Type type, Object value, Column column) {
        this(dataType, type, value, column, null);
    }

    public enum DataType {
        @JsonProperty("text")
        TEXT,
        @JsonProperty("number")
        NUMBER,
    }

    public enum Type {
        @JsonProperty("equals")
        EQUALS,
        @JsonProperty("contains")
        CONTAINS,
        @JsonProperty("in")
        IN,
        @JsonProperty("startsWith")
        STARTS_WITH,
        @JsonProperty("notEqual")
        NOT_EQUAL,
        @JsonProperty("lessThanOrEqual")
        LESS_THAN_OR_EQUAL,
        @JsonProperty("greaterThanOrEqual")
        GREATER_THAN_OR_EQUAL
    }

    public enum Column {


        // Limit violation columns
        @JsonProperty("subjectId")
        SUBJECT_ID("subjectId"),
        @JsonProperty("limit")
        LIMIT("limit"),
        @JsonProperty("limitName")
        LIMIT_NAME("limitName"),
        @JsonProperty("limitType")
        LIMIT_TYPE("limitType"),
        @JsonProperty("actualOverload")
        ACTUEL_OVERLOAD("actualOverload"),
        @JsonProperty("value")
        VALUE("value"),
        @JsonProperty("side")
        SIDE("side"),
        @JsonProperty("upComingOverload")
        UP_COMING_OVERLOAD("upComingOverload"),
        @JsonProperty("overload")
        OVERLOAD("overload"),

        // Loadflow result columns
        @JsonProperty("connectedComponentNum")
        CONNECTED_COMPONENT_NUM("connectedComponentNum"),

        @JsonProperty("synchronousComponentNum")
        SYNCHRONOUS_COMPONENT_NUM("synchronousComponentNum"),

        @JsonProperty("status")
        STATUS("status"),
        @JsonProperty("iterationCount")
        ITERATION_COUNT("iterationCount"),

        @JsonProperty("activePowerMismatch")
        ACTIVE_POWER_MISMATCH("activePowerMismatch"),

        @JsonProperty("id")
        ID("id"),

        @JsonProperty("distributedActivePower")
        DISTRIBUTED_ACTIVE_POWER("distributedActivePower");

        private final String columnName;

        Column(String columnName) {
            this.columnName = columnName;
        }

        public String columnName() {
            return columnName;
        }
    }
}
