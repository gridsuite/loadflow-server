/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CountryAdequacy {
    public enum ValueType {
        LOAD, GENERATION, LOSSES, NET_POSITION
    };

    private UUID countryAdequacyUuid;

    String country;

    double load;

    double generation;

    double losses;

    double netPosition;
}
