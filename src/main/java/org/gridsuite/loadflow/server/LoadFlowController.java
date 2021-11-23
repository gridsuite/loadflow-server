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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.gridsuite.loadflow.utils.ReportInfos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

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

    @PutMapping(value = "/networks/{networkUuid}/run", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "run a load flow on a network")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The load flow has been performed")})
    public ResponseEntity<LoadFlowResult> loadFlow(@Parameter(description = "Network UUID") @PathVariable("networkUuid") UUID networkUuid,
                                                   @Parameter(description = "Variant Id") @RequestParam(name = "variantId", required = false) String variantId,
                                                   @Parameter(description = "Other networks UUID") @RequestParam(name = "networkUuid", required = false) List<String> otherNetworks,
                                                   @Parameter(description = "Provider") @RequestParam(name = "provider", required = false) String provider,
                                                   @Parameter(description = "reportId") @RequestParam(name = "reportId", required = false) UUID reportId,
                                                   @Parameter(description = "reportName") @RequestParam(name = "reportName", required = false) String reportName,
                                                   @Parameter(description = "overwriteReport") @RequestParam(name = "overwriteReport", required = false, defaultValue = "true") Boolean overwriteReport,
                                                   @RequestBody(required = false) String loadflowParams) {
        LoadFlowParameters parameters = loadflowParams != null
                ? JsonLoadFlowParameters.read(new ByteArrayInputStream(loadflowParams.getBytes()))
                : null;

        List<UUID> otherNetworksUuid = otherNetworks != null ? otherNetworks.stream().map(UUID::fromString).collect(Collectors.toList()) : Collections.emptyList();

        LoadFlowResult result = loadFlowService.loadFlow(networkUuid, variantId, otherNetworksUuid, parameters, provider,
            new ReportInfos(reportId, reportName, overwriteReport));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
