/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import io.swagger.annotations.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + LoadFlowApi.API_VERSION + "/")
@Api(tags = "loadflow-server")
@ComponentScan(basePackageClasses = LoadFlowService.class)
public class LoadFlowController {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowController.class);

    @Inject
    private LoadFlowService loadFlowService;

    @PutMapping(value = "/networks/{networkUuid}")
    @ApiOperation(value = "run a load flow on a network")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The load flow has been performed")})
    public ResponseEntity<Void> loadFlow(@ApiParam(value = "Network UUID") @PathVariable("networkUuid") UUID networkUuid) {
        loadFlowService.loadFlow(networkUuid);
        return ResponseEntity.ok().build();
    }
}
