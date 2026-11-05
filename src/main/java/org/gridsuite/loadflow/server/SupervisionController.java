/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.gridsuite.loadflow.server.service.SupervisionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + LoadFlowApi.API_VERSION + "/supervision")
@Tag(name = "Loadflow server - Supervision")
public class SupervisionController {
    private final SupervisionService supervisionService;
    private final LoadFlowService loadFlowService;

    public SupervisionController(SupervisionService supervisionService, LoadFlowService loadFlowService) {
        this.supervisionService = supervisionService;
        this.loadFlowService = loadFlowService;
    }

    @GetMapping(value = "/results-count")
    @Operation(summary = "Get results count")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The count of all results")})
    public ResponseEntity<Integer> getResultsCount() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(supervisionService.getResultsCount());
    }

    @DeleteMapping(value = "/results")
    @Operation(summary = "Delete all loadflow results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All loadflow results have been deleted")})
    public ResponseEntity<Void> deleteAllResults() {
        loadFlowService.deleteAllResults();
        return ResponseEntity.ok().build();
    }
}
