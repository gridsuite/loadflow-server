/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto;

/**
 * @author Anis Touri <anis.touri at rte-france.com
 */
public enum LoadFlowStatus {
    NOT_DONE,
    RUNNING,
    CONVERGED,
    DIVERGED,
}
