/**
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto.parameters;

import com.powsybl.loadflow.LoadFlowParameters;

import lombok.Builder;

import java.util.Map;
import java.util.UUID;

import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Builder
public record LoadFlowParametersInfos(
    UUID uuid,
    LoadFlowParameters commonParameters,
    Map<String, Map<String, String>> specificParametersPerProvider) {
    public LoadFlowParametersEntity toEntity() {
        return new LoadFlowParametersEntity(this);
    }
}
