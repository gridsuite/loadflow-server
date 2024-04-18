/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.computation.utils.annotations;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */

public class PostCompletionException extends RuntimeException {
    public PostCompletionException(Throwable t) {
        super(t);
    }
}
