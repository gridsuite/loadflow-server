/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.loadflow.LoadFlowResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.NonNull;
import org.gridsuite.loadflow.server.dto.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.service.computation.AbstractComputationObserver;
import org.springframework.stereotype.Service;

import static org.gridsuite.loadflow.server.service.LoadFlowWorkerService.LOADFLOW_LABEL;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public class LoadflowObserver extends AbstractComputationObserver<LoadFlowResult, LoadFlowParametersInfos> {

    public LoadflowObserver(@NonNull ObservationRegistry observationRegistry, @NonNull MeterRegistry meterRegistry) {
        super(observationRegistry, meterRegistry, LOADFLOW_LABEL);
    }

    protected String getResultStatus(LoadFlowResult res) {
        return res != null && res.isOk() ? "OK" : "NOK";
    }
}
