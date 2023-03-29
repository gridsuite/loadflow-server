/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterScope;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.loadflow.server.dto.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.ParameterInfos;
import org.gridsuite.loadflow.server.utils.ReportContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.loadflow.server.LoadFlowConstants.DELIMITER;
import static org.gridsuite.loadflow.server.LoadFlowConstants.REPORT_API_VERSION;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
class LoadFlowService {

    @Value("${gridsuite.services.report-server.base-uri:http://report-server}")
    String reportServerURI;

    @Value("${loadflow.default-provider}")
    private String defaultProvider;

    @Autowired
    private NetworkStoreService networkStoreService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String LOAD_FLOW_TYPE_REPORT = "LoadFlow";

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private LoadFlowParameters buildLoadFlowParameters(LoadFlowParametersInfos loadflowParams, String provider) {
        if (loadflowParams == null || loadflowParams.getCommonParameters() == null) {
            return new LoadFlowParameters(); // default params
        }
        LoadFlowParameters params = loadflowParams.getCommonParameters();
        if (loadflowParams.getSpecificParameters() == null || loadflowParams.getSpecificParameters().isEmpty()) {
            return params; // no specific params
        }
        LoadFlowProvider lfProvider = LoadFlowProvider.findAll().stream()
                .filter(p -> p.getName().equals(provider))
                .findFirst().orElseThrow(() -> new PowsyblException("Model not found " + provider));
        Extension<LoadFlowParameters> extension = lfProvider.loadSpecificParameters(loadflowParams.getSpecificParameters())
                .orElseThrow(() -> new PowsyblException("Cannot add specific parameters with model " + provider));
        params.addExtension((Class) extension.getClass(), extension);
        return params;
    }

    LoadFlowResult run(UUID networkUuid, String variantId, List<UUID> otherNetworksUuid, LoadFlowParametersInfos loadflowParams,
                       String provider, ReportContext reportContext) {
        String providerToUse = provider != null ? provider : defaultProvider;
        LoadFlowParameters params = buildLoadFlowParameters(loadflowParams, providerToUse);
        Reporter rootReporter = Reporter.NO_OP;
        Reporter reporter = Reporter.NO_OP;
        if (reportContext.getReportId() != null) {
            String rootReporterId = reportContext.getReportName() == null ? LOAD_FLOW_TYPE_REPORT : reportContext.getReportName() +  "@" + LOAD_FLOW_TYPE_REPORT;
            rootReporter = new ReporterModel(rootReporterId, rootReporterId);
            reporter = rootReporter.createSubReporter(LOAD_FLOW_TYPE_REPORT, LOAD_FLOW_TYPE_REPORT + " (${providerToUse})", "providerToUse", providerToUse);
        }

        LoadFlow.Runner runner = LoadFlow.find(providerToUse);
        LoadFlowResult result;
        if (otherNetworksUuid.isEmpty()) {
            Network network = getNetwork(networkUuid);
            // launch the load flow on the network
            result = runner.run(network, variantId != null ? variantId : VariantManagerConstants.INITIAL_VARIANT_ID, LocalComputationManager.getDefault(), params, reporter);
            // flush network in the network store
            if (result.isOk()) {
                networkStoreService.flush(network);
            }
        } else {
            // creation of the merging view and merging the networks
            MergingView mergingView = MergingView.create("merged", "iidm");
            List<Network> networks = new ArrayList<>();
            networks.add(getNetwork(networkUuid));
            otherNetworksUuid.forEach(uuid -> networks.add(getNetwork(uuid)));
            mergingView.merge(networks.toArray(new Network[0]));

            // launch the load flow on the merging view
            result = runner.run(mergingView, variantId != null ? variantId : VariantManagerConstants.INITIAL_VARIANT_ID, LocalComputationManager.getDefault(), params, reporter);
            if (result.isOk()) {
                // flush each network of the merging view in the network store
                networks.forEach(network -> networkStoreService.flush(network));
            }
        }
        if (reportContext.getReportId() != null) {
            sendReport(reportContext.getReportId(), rootReporter);
        }

        return result;
    }

    private void sendReport(UUID reportId, Reporter reporter) {
        var restTemplate = new RestTemplate();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resourceUrl = reportServerURI + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER + reportId.toString();
        var uriBuilder = UriComponentsBuilder.fromHttpUrl(resourceUrl);
        try {
            restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(reporter), headers), ReporterModel.class);
        } catch (JsonProcessingException error) {
            throw new PowsyblException("error creating report", error);
        }
    }

    List<String> getProviders() {
        return LoadFlowProvider.findAll().stream()
                .map(LoadFlowProvider::getName)
                .collect(Collectors.toList());
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    private static ParameterInfos toParameterInfos(Parameter param) {
        return ParameterInfos.builder()
            .name(param.getName())
            .type(param.getType())
            .description(param.getDescription())
            .defaultValue(param.getDefaultValue())
            .possibleValues(param.getPossibleValues())
            .build();
    }

    public List<ParameterInfos> getSpecificLoadFlowParameters(String providerName) {
        Objects.requireNonNull(providerName);
        LoadFlowProvider lfProvider = LoadFlowProvider.findAll().stream()
            .filter(provider -> provider.getName().equals(providerName))
            .findFirst().orElseThrow(() -> new PowsyblException("Model not found " + providerName));
        return lfProvider.getSpecificParameters().stream()
            .filter(p -> p.getScope() == ParameterScope.FUNCTIONAL)
            .map(LoadFlowService::toParameterInfos).collect(Collectors.toList());
    }
}
