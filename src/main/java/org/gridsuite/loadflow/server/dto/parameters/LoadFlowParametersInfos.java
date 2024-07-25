/**
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto.parameters;

import com.powsybl.loadflow.LoadFlowParameters;

import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class LoadFlowParametersInfos {
    private UUID uuid;
    private String provider;
    private LoadFlowParameters commonParameters;
    private Map<String, Map<String, String>> specificParametersPerProvider;

    // Only to set/get values from db
    private List<List<Double>> limitReductionsValues;

    @Setter
    private List<LimitReductionsByVoltageLevel> limitReductions;

    public LoadFlowParametersEntity toEntity() {
        return new LoadFlowParametersEntity(this);
    }
}
