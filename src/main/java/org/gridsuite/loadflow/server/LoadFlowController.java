/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.loadflow.LoadFlowResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.loadflow.server.dto.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.service.LoadFlowRunContext;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.gridsuite.loadflow.server.service.LoadFlowWorkerService;
import org.gridsuite.loadflow.server.utils.ReportContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + LoadFlowApi.API_VERSION + "/")
@Tag(name = "loadflow-server")
@ComponentScan(basePackageClasses = LoadFlowService.class)
public class LoadFlowController {

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private LoadFlowWorkerService loadFlowWorkerService;

    @PutMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a load flow on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow has been performed")})
    public ResponseEntity<LoadFlowResult> run(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                              @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                              @Parameter(description = "Other networks UUID") @RequestParam(name = "networkUuid", required = false) List<String> otherNetworks,
                                              @Parameter(description = "Provider") @RequestParam(name = "provider", required = false) String provider,
                                              @Parameter(description = "reportId") @RequestParam(name = "reportId", required = false) UUID reportId,
                                              @Parameter(description = "reportName") @RequestParam(name = "reportName", required = false) String reportName,
                                              @RequestBody(required = false) LoadFlowParametersInfos loadflowParams) {
        String providerToUse = provider != null ? provider : loadFlowService.getDefaultProvider();
        List<UUID> otherNetworksUuid = otherNetworks != null ? otherNetworks.stream().map(UUID::fromString).collect(Collectors.toList()) : Collections.emptyList();
        Mono<LoadFlowResult> result = loadFlowWorkerService.run(new LoadFlowRunContext(networkUuid, variantId, otherNetworksUuid, null, providerToUse, loadflowParams, new ReportContext(reportId, reportName)));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result.block());
    }

    @GetMapping(value = "/providers", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all loadflow providers")
    @ApiResponses(value = {@ApiResponse(responseCode = "200")})
    public ResponseEntity<List<String>> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(loadFlowService.getProviders());
    }

    @GetMapping(value = "/default-provider", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Get loadflow default provider")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "The load flow default provider has been found"))
    public ResponseEntity<String> getDefaultLoadflowProvider() {
        return ResponseEntity.ok().body(loadFlowService.getDefaultProvider());
    }

    @GetMapping(value = "/specific-parameters")
    @Operation(summary = "Get all existing loadflow specific parameters for a given provider, or for all of them")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow model-specific parameters")})
    public ResponseEntity<Map<String, List<com.powsybl.commons.parameters.Parameter>>> getSpecificLoadflowParameters(
            @Parameter(description = "The model provider") @RequestParam(name = "provider", required = false) String provider) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(loadFlowService.getSpecificLoadFlowParameters(provider));
    }
}
