/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author maissa Souissi <maissa.souissi at rte-france.com>
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GlobalFilter {
    List<String> nominalV;
    List<String> countryCode;
    String limitViolationsType;
}
