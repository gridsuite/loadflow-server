/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.computation.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.loadflow.server.computation.utils.ReportContext;

import java.util.UUID;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com
 * @param <P> parameters structure specific to the computation
 */
@Getter
@AllArgsConstructor
public abstract class AbstractComputationRunContext<P> {
    private final UUID networkUuid;
    private final String variantId;
    private final String receiver;
    private final ReportContext reportContext;
    private final String userId;
    @Setter protected String provider;
    @Setter protected P parameters;
}
