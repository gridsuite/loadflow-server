/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.service.parameters.LoadFlowParametersService;
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

    private final LoadFlowParametersService parametersService;

    public LoadFlowParametersController(LoadFlowParametersService parametersService) {
        this.parametersService = parametersService;
    }

    @PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create parameters")
    @ApiResponse(responseCode = "200", description = "parameters were created")
    public ResponseEntity<UUID> createParameters(
            @RequestBody LoadFlowParametersInfos parametersInfos) {
        return ResponseEntity.ok(parametersService.createParameters(parametersInfos));
    }

    @PostMapping(value = "/default")
    @Operation(summary = "Create default parameters")
    @ApiResponse(responseCode = "200", description = "Default parameters were created")
    public ResponseEntity<UUID> createDefaultParameters() {
        return ResponseEntity.ok(parametersService.createDefaultParameters());
    }

    @PostMapping(value = "/{sourceParametersUuid}")
    @Operation(summary = "Duplicate parameters")
    @ApiResponse(responseCode = "200", description = "parameters were duplicated")
    public ResponseEntity<UUID> duplicateParameters(
            @Parameter(description = "source parameters UUID") @PathVariable("sourceParametersUuid") UUID sourceParametersUuid) {
        return ResponseEntity.of(parametersService.duplicateParameters(sourceParametersUuid));
    }

    @GetMapping(value = "/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get parameters")
    @ApiResponse(responseCode = "200", description = "parameters were returned")
    @ApiResponse(responseCode = "404", description = "parameters were not found")
    public ResponseEntity<LoadFlowParametersInfos> getParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid) {
        return ResponseEntity.of(parametersService.getParameters(parametersUuid));
    }

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all parameters")
    @ApiResponse(responseCode = "200", description = "The list of all parameters was returned")
    public ResponseEntity<List<LoadFlowParametersInfos>> getAllParameters() {
        return ResponseEntity.ok().body(parametersService.getAllParameters());
    }

    @PutMapping(value = "/{uuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update parameters")
    @ApiResponse(responseCode = "200", description = "parameters were updated")
    public ResponseEntity<Void> updateParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid,
            @RequestBody(required = false) LoadFlowParametersInfos parametersInfos) {
        parametersService.updateParameters(parametersUuid, parametersInfos);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/{uuid}")
    @Operation(summary = "Delete parameters")
    @ApiResponse(responseCode = "200", description = "parameters were deleted")
    public ResponseEntity<Void> deleteParameters(
            @Parameter(description = "parameters UUID") @PathVariable("uuid") UUID parametersUuid) {
        parametersService.deleteParameters(parametersUuid);
        return ResponseEntity.ok().build();
    }
}
