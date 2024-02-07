/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.utils;

import java.util.Objects;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */

public class LoadflowException extends RuntimeException {
    public enum Type {
        RESULT_NOT_FOUND,
        INVALID_FILTER_FORMAT,
        INVALID_SORT_FORMAT,
        INVALID_FILTER
    }

    private final Type type;

    public LoadflowException(Type type) {
        super(Objects.requireNonNull(type.name()));
        this.type = type;
    }

    public LoadflowException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
