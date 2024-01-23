/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service.computation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.gridsuite.loadflow.server.utils.ReportContext;

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
    protected final String provider;
    private final ReportContext reportContext;
    private final String userId;
    private final Float limitReduction;
    protected final P parameters;
}
