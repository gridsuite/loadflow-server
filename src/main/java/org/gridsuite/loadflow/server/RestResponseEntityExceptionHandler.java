/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server;

import com.powsybl.ws.commons.error.AbstractBaseRestExceptionHandler;
import com.powsybl.ws.commons.error.ServerNameProvider;
import lombok.NonNull;
import org.gridsuite.computation.ComputationBusinessErrorCode;
import org.gridsuite.computation.ComputationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler extends AbstractBaseRestExceptionHandler<ComputationException, ComputationBusinessErrorCode> {

    protected RestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected @NonNull ComputationBusinessErrorCode getBusinessCode(ComputationException e) {
        return e.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(ComputationBusinessErrorCode businessErrorCode) {
        return switch (businessErrorCode) {
            case RESULT_NOT_FOUND, PARAMETERS_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_SORT_FORMAT -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
