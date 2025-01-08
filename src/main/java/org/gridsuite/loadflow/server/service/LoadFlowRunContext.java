/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import lombok.Builder;
import lombok.Getter;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import com.powsybl.ws.commons.computation.service.AbstractComputationRunContext;
import com.powsybl.ws.commons.computation.dto.ReportInfos;

import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
public class LoadFlowRunContext extends AbstractComputationRunContext<LoadFlowParametersValues> {
    private final Float limitReduction;
    private final UUID parametersUuid;

    public LoadFlowParameters buildParameters() {
        LoadFlowParameters params = getParameters() == null || getParameters().getCommonParameters() == null ?
                LoadFlowParameters.load() : getParameters().getCommonParameters();
        if (getParameters() == null || getParameters().getSpecificParameters() == null || getParameters().getSpecificParameters().isEmpty()) {
            return params; // no specific LF params
        }
        LoadFlowProvider lfProvider = LoadFlowProvider.findAll().stream()
                .filter(p -> p.getName().equals(getProvider()))
                .findFirst().orElseThrow(() -> new PowsyblException("LoadFLow provider not found " + getProvider()));

        Extension<LoadFlowParameters> specificParametersExtension = lfProvider.loadSpecificParameters(PlatformConfig.defaultConfig())
                .orElseThrow(() -> new PowsyblException("Cannot add specific loadflow parameters with provider " + getProvider()));
        params.addExtension((Class) specificParametersExtension.getClass(), specificParametersExtension);
        lfProvider.updateSpecificParameters(specificParametersExtension, getParameters().getSpecificParameters());

        return params;
    }

    @Builder
    public LoadFlowRunContext(UUID networkUuid, String variantId, String receiver, String provider, ReportInfos reportInfos, String userId,
                              Float limitReduction, LoadFlowParametersValues parameters, UUID parametersUuid) {
        super(networkUuid, variantId, receiver, reportInfos, userId, provider, parameters);
        this.limitReduction = limitReduction;
        this.parametersUuid = parametersUuid;
    }
}
