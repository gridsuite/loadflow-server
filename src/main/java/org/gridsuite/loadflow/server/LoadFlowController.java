/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + LoadFlowApi.API_VERSION + "/")
@Api(tags = "loadflow-server")
@ComponentScan(basePackageClasses = LoadFlowService.class)
public class LoadFlowController {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowController.class);

    @Autowired
    private LoadFlowService loadFlowService;

    @PutMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE)
    @ApiOperation(value = "run a load flow on a network", produces = APPLICATION_JSON_VALUE)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "The load flow has been performed")})
    public ResponseEntity<LoadFlowResult> loadFlow(@ApiParam(value = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                   @ApiParam(value = "Other networks UUID") @RequestParam(name = "networkUuid", required = false) List<String> otherNetworks,
                                                   @ApiParam(value = "reportId") @RequestParam(name = "reportId", required = false) UUID reportId,
                                                   @ApiParam(value = "reportName") @RequestParam(name = "reportName", required = false) String reportName,
                                                   @RequestBody(required = false) String loadflowParams) {
        LoadFlowParameters parameters = loadflowParams != null
                ? JsonLoadFlowParameters.read(new ByteArrayInputStream(loadflowParams.getBytes()))
                : null;

        List<UUID> otherNetworksUuid = otherNetworks != null ? otherNetworks.stream().map(UUID::fromString).collect(Collectors.toList()) : Collections.emptyList();

        LoadFlowResult result = loadFlowService.loadFlow(networkUuid, otherNetworksUuid, parameters, Optional.ofNullable(reportId), Optional.ofNullable(reportName));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
