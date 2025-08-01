/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.security.LimitViolationType;
import com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.computation.dto.ReportInfos;
import org.gridsuite.computation.service.UuidGeneratorService;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.LoadFlowResult;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;
import org.gridsuite.loadflow.server.service.LoadFlowResultService;
import org.gridsuite.loadflow.server.service.LoadFlowRunContext;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.computation.service.NotificationService.HEADER_USER_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + LoadFlowApi.API_VERSION + "/")
@Tag(name = "loadflow-server")
public class LoadFlowController {

    private final LoadFlowService loadFlowService;
    private final UuidGeneratorService uuidGeneratorService;
    private final LoadFlowResultService loadFlowResultService;

    public LoadFlowController(LoadFlowService loadFlowService, LoadFlowResultService loadFlowResultService, UuidGeneratorService uuidGeneratorService) {
        this.loadFlowService = loadFlowService;
        this.uuidGeneratorService = uuidGeneratorService;
        this.loadFlowResultService = loadFlowResultService;
    }

    @PostMapping(value = "/networks/{networkUuid}/run-and-save", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Run a load flow on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow has been performed")})
    public ResponseEntity<UUID> run(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                    @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                    @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                    @Parameter(description = "reportUuid") @RequestParam(name = "reportUuid", required = false) UUID reportId,
                                    @Parameter(description = "reporterId") @RequestParam(name = "reporterId", required = false) String reportName,
                                    @Parameter(description = "The type name for the report") @RequestParam(name = "reportType", required = false, defaultValue = "LoadFlow") String reportType,
                                    @Parameter(description = "parametersUuid") @RequestParam(name = "parametersUuid", required = false) UUID parametersUuid,
                                    @Parameter(description = "withRatioTapChangers") @RequestParam(name = "withRatioTapChangers", required = false, defaultValue = "false") Boolean withRatioTapChangers,
                                    @Parameter(description = "resultUuid") @RequestParam(name = "resultUuid", required = false) UUID resultUuid,
                                    @RequestHeader(HEADER_USER_ID) String userId
                                    ) {
        UUID resultUuidToRun = resultUuid != null ? resultUuid : uuidGeneratorService.generate();
        LoadFlowRunContext loadFlowRunContext = LoadFlowRunContext.builder()
                .resultUuid(resultUuidToRun)
                .networkUuid(networkUuid)
                .variantId(variantId)
                .receiver(receiver)
                .reportInfos(ReportInfos.builder().reportUuid(reportId).reporterId(reportName).computationType(reportType).build())
                .userId(userId)
                .parametersUuid(parametersUuid)
                .withRatioTapChangers(withRatioTapChangers)
                .build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowService.runAndSaveResult(loadFlowRunContext));
    }

    @GetMapping(value = "/results/{resultUuid}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a loadflow result from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow result"),
        @ApiResponse(responseCode = "404", description = "The loadflow result has not been found")})
    public ResponseEntity<LoadFlowResult> getResult(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                                    @Parameter(description = "Filters") @RequestParam(name = "filters", required = false) String stringFilters,
                                                    @Parameter(description = "Sort parameters") Sort sort) {
        String decodedStringFilters = stringFilters != null ? URLDecoder.decode(stringFilters, StandardCharsets.UTF_8) : null;
        LoadFlowResult result = loadFlowResultService.getResult(resultUuid, decodedStringFilters, sort);
        return result != null ? ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result)
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping(value = "/results", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete loadflow results from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow results has been deleted")})
    public ResponseEntity<Void> deleteResults(@Parameter(description = "Results UUID") @RequestParam(value = "resultsUuids", required = false) List<UUID> resultsUuids) {
        loadFlowService.deleteResults(resultsUuids);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/results/{resultUuid}/status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the loadflow status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow status")})
    public ResponseEntity<LoadFlowStatus> getStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        return ResponseEntity.ok().body(loadFlowService.getStatus(resultUuid));
    }

    @PutMapping(value = "/results/invalidate-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Invalidate the loadflow status from the database")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow status has been invalidated")})
    public ResponseEntity<Void> invalidateStatus(@Parameter(description = "Result uuids") @RequestParam(name = "resultUuid") List<UUID> resultUuids) {
        loadFlowService.setStatus(resultUuids, LoadFlowStatus.NOT_DONE);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/results/running-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Create running status for a random UUID then return it")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow status has been created")})
    public ResponseEntity<UUID> createRunningStatus() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowService.createRunningStatus());
    }

    @PutMapping(value = "/results/{resultUuid}/stop", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Stop a loadflow computation")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The loadflow has been stopped")})
    public ResponseEntity<Void> stop(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
                                     @Parameter(description = "Result receiver") @RequestParam(name = "receiver", required = false) String receiver,
                                     @RequestHeader(HEADER_USER_ID) String userId) {
        loadFlowService.stop(resultUuid, receiver, userId);
        return ResponseEntity.ok().build();
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
                .body(LoadFlowService.getSpecificLoadFlowParameters(provider));
    }

    @GetMapping(value = "/results/{resultUuid}/limit-violations")
    @Operation(summary = "Get limit violations")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The limit violations")})
    public ResponseEntity<List<LimitViolationInfos>> getLimitViolations(
            @Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid,
            @Parameter(description = "Filters") @RequestParam(name = "filters", required = false) String filters,
            @Parameter(description = "Global Filters") @RequestParam(name = "globalFilters", required = false) String globalFilters,
            @Parameter(description = "Sort parameters") Sort sort,
            @Parameter(description = "network Uuid") @RequestParam(name = "networkUuid", required = false) UUID networkUuid,
            @Parameter(description = "variant Id") @RequestParam(name = "variantId", required = false) String variantId
    ) {
        String decodedStringFilters = filters != null ? URLDecoder.decode(filters, StandardCharsets.UTF_8) : null;
        String decodedStringGlobalFilters = globalFilters != null ? URLDecoder.decode(globalFilters, StandardCharsets.UTF_8) : null;
        List<LimitViolationInfos> result = loadFlowResultService.getLimitViolationsInfos(resultUuid, decodedStringFilters, decodedStringGlobalFilters, sort, networkUuid, variantId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping(value = "/results/{resultUuid}/limit-types", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the list of limit types values")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List of limit types values by result"))
    public ResponseEntity<List<LimitViolationType>> getLimitTypes(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowResultService.getLimitTypes(resultUuid));
    }

    @GetMapping(value = "/results/{resultUuid}/branch-sides", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the list of branch sides values")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List of branch sides values by result"))
    public ResponseEntity<List<TwoSides>> getBranchSides(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowResultService.getBranchSides(resultUuid));
    }

    @GetMapping(value = "/results/{resultUuid}/computation-status", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the list of computation status values")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "List of computation status values by result"))
    public ResponseEntity<List<Status>> getComputationStatus(@Parameter(description = "Result UUID") @PathVariable("resultUuid") UUID resultUuid) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(loadFlowService.getComputationStatus(resultUuid));
    }
}
