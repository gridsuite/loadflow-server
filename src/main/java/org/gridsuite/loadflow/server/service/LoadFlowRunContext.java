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
import org.gridsuite.loadflow.server.dto.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.utils.ReportContext;

import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
@Builder
public class LoadFlowRunContext {

    private final UUID networkUuid;

    private final String variantId;

    private final String receiver;

    private final String provider;

    private final LoadFlowParametersInfos parameters;

    private final ReportContext reportContext;

    private final String userId;

    private final Float limitReduction;

    public static LoadFlowParameters buildParameters(LoadFlowParametersInfos parameters, String provider) {
        LoadFlowParameters params = parameters == null || parameters.getSpecificParameters() == null ?
                LoadFlowParameters.load() : parameters.getCommonParameters();
        if (parameters == null || parameters.getSpecificParameters() == null || parameters.getSpecificParameters().isEmpty()) {
            return params; // no specific LF params
        }
        LoadFlowProvider lfProvider = LoadFlowProvider.findAll().stream()
                .filter(p -> p.getName().equals(provider))
                .findFirst().orElseThrow(() -> new PowsyblException("LoadFLow provider not found " + provider));
        Extension<LoadFlowParameters> extension = lfProvider.loadSpecificParameters(parameters.getSpecificParameters())
                .orElseThrow(() -> new PowsyblException("Cannot add specific loadflow parameters with provider " + provider));
        params.addExtension((Class) extension.getClass(), extension);
        return params;
    }
}
