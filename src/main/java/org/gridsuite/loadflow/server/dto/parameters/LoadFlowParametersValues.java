/*
  Copyright (c) 2023, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto.parameters;

import com.powsybl.loadflow.LoadFlowParameters;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * @author David Braquart <david.braquart@rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class LoadFlowParametersValues {
    private String provider;
    private LoadFlowParameters commonParameters;
    private Map<String, String> specificParameters;

    // Only to set/get values from db
    @Setter
    private List<List<Double>> limitReductionsValues;

    @Setter
    private List<LimitReductionsByVoltageLevel> limitReductions;
}
