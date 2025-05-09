/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Anis TOURI <anis.touri@rte-france.com>
 */
public enum Column {
    // Limit violation columns
    @JsonProperty("subjectId")
    SUBJECT_ID("subjectId"),
    @JsonProperty("locationId")
    LOCATION_ID("locationId"),
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
