/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
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
import org.gridsuite.loadflow.server.dto.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.LoadFlowResult;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;
import org.gridsuite.loadflow.server.service.LoadFlowRunContext;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.gridsuite.loadflow.server.utils.ReportContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.loadflow.server.service.LoadFlowRunContext.buildParameters;
import static org.gridsuite.loadflow.server.service.NotificationService.HEADER_USER_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + LoadFlowApi.API_VERSION + "/")
@Tag(name = "loadflow-server")
public class LoadFlowController {

    @Autowired
    private LoadFlowService loadFlowService;

    @PostMapping(value = "/networks/{networkUuid}/run-and-save", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a load flow on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow has been performed")})
    public ResponseEntity<UUID> run(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                    @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                    @Parameter(description = "Other networks UUID") @RequestParam(name = "networkUuid", required = false) List<String> otherNetworks,
                                    @Parameter(description = "Provider") @RequestParam(name = "provider", required = false) String provider,
                                    @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                    @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportId,
                                    @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reportName,
                                    @RequestHeader(HEADER_USER_ID) String userId,
                                    @RequestBody(required = false) LoadFlowParametersInfos loadflowParams
                                    ) {
        String providerToUse = provider != null ? provider : loadFlowService.getDefaultProvider();
        List<UUID> otherNetworksUuids = otherNetworks != null ? otherNetworks.stream().map(UUID::fromString).collect(Collectors.toList()) : Collections.emptyList();
        LoadFlowRunContext loadFlowRunContext = LoadFlowRunContext.builder()
                .networkUuid(networkUuid)
                .variantId(variantId)
                .otherNetworksUuids(otherNetworksUuids)
                .receiver(receiver)
                .provider(providerToUse)
                .parameters(buildParameters(loadflowParams, provider))
                .reportContext(ReportContext.builder().reportId(reportId).reportName(reportName).build())
                .userId(userId)
                .build();
        UUID resultUuid = loadFlowService.runAndSaveResult(loadFlowRunContext);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resultUuid);
    }

    @GetMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a loadflow result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow result"),
            @ApiResponse(responseCode = "404", description = "The loadflow result has not been found")})
    public ResponseEntity<LoadFlowResult> getResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        LoadFlowResult result = loadFlowService.getResult(resultUuid);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a loadflow result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow result has been deleted")})
    public ResponseEntity<Void> deleteResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        loadFlowService.deleteResult(resultUuid);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete loadflow results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow results has been deleted")})
    public ResponseEntity<Void> deleteResults(@Parameter(description = "Results UUID") @RequestParam("resultsUuids") List<UUID> resultsUuids) {
        loadFlowService.deleteResults(resultsUuids);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/results/all", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete all loadflow results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "All loadflow results have been deleted")})
    public ResponseEntity<Void> deleteResults() {
        loadFlowService.deleteResults();
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the loadflow status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow status")})
    public ResponseEntity<String> getStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        LoadFlowStatus status = loadFlowService.getStatus(resultUuid);
        return ResponseEntity.ok().body(status != null ? status.name() : null);
    }

    @PutMapping(value = "/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Invalidate the loadflow status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow status has been invalidated")})
    public ResponseEntity<Void> invalidateStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
        loadFlowService.setStatus(resultUuids, LoadFlowStatus.NOT_DONE);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a loadflow computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has been stopped")})
    public ResponseEntity<Void> stop(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                     @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver) {
        loadFlowService.stop(resultUuid, receiver);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/providers", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all loadflow providers")
    @ApiResponses(value = {@ApiResponse(responseCode = "200")})
    public ResponseEntity<List<String>> getProviders() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(LoadFlowService.getProviders());
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
                .body(LoadFlowService.getSpecificLoadFlowParameters(provider));
    }
}
