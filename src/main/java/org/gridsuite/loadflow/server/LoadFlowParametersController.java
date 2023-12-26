/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.service.parameters.LoadFlowParametersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + LoadFlowApi.API_VERSION + "/parameters")
@Tag(name = "LoadFlow parameters")
public class LoadFlowParametersController {

    @Autowired
    private LoadFlowParametersService parametersService;

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "parameters were created")})
    public ResponseEntity<UUID> createParameters(
            @RequestBody LoadFlowParametersInfos parametersInfos) {
        return ResponseEntity.ok().body(parametersService.createParameters(parametersInfos));
    }

    @GetMapping(value = "/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get parameters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "parameters were returned"),
        @ApiResponse(responseCode = "404", description = "parameters were not found")})
    public ResponseEntity<LoadFlowParametersInfos> getParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid) {
        LoadFlowParametersInfos parameters = parametersService.getParameters(parametersUuid);
        return parameters != null ? ResponseEntity.ok().body(parametersService.getParameters(parametersUuid))
                : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all parameters")
    @ApiResponse(responseCode = "200", description = "The list of all parameters was returned")
    public ResponseEntity<List<LoadFlowParametersInfos>> getAllParameters() {
        return ResponseEntity.ok().body(parametersService.getAllParameters());
    }

    @PutMapping(value = "/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update parameters")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "parameters were updated")})
    public ResponseEntity<Void> updateParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid,
            @RequestBody LoadFlowParametersInfos parametersInfos) {
        parametersService.updateParameters(parametersUuid, parametersInfos);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete parameters")
    @ApiResponse(responseCode = "200", description = "parameters were deleted")
    public ResponseEntity<Void> deleteParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid) {
        parametersService.deleteParameters(parametersUuid);
        return ResponseEntity.ok().build();
    }
}
