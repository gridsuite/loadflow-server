/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import lombok.Builder;
import lombok.Getter;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.computation.service.AbstractComputationRunContext;
import org.gridsuite.loadflow.server.computation.utils.ReportContext;

import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
public class LoadFlowRunContext extends AbstractComputationRunContext<LoadFlowParametersValues> {

    public LoadFlowParameters buildParameters() {
        LoadFlowParameters params = parameters == null || parameters.specificParameters() == null ?
                LoadFlowParameters.load() : parameters.commonParameters();
        if (parameters == null || parameters.specificParameters() == null || parameters.specificParameters().isEmpty()) {
            return params; // no specific LF params
        }
        LoadFlowProvider lfProvider = LoadFlowProvider.findAll().stream()
                .filter(p -> p.getName().equals(provider))
                .findFirst().orElseThrow(() -> new PowsyblException("LoadFLow provider not found " + provider));
        Extension<LoadFlowParameters> extension = lfProvider.loadSpecificParameters(parameters.specificParameters())
                .orElseThrow(() -> new PowsyblException("Cannot add specific loadflow parameters with provider " + provider));
        params.addExtension((Class) extension.getClass(), extension);
        return params;
    }

    @Builder
    public LoadFlowRunContext(UUID networkUuid, String variantId, String receiver, String provider, ReportContext reportContext, String userId,
                              Float limitReduction, LoadFlowParametersValues parameters) {
        super(networkUuid, variantId, receiver, reportContext, userId, limitReduction, provider, parameters);
    }
}
