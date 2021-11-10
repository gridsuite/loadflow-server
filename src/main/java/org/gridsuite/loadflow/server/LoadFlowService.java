/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelDeserializer;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.gridsuite.loadflow.utils.ReportInfos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.loadflow.server.LoadFlowConstants.DELIMITER;
import static org.gridsuite.loadflow.server.LoadFlowConstants.REPORT_API_VERSION;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
class LoadFlowService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowService.class);

    @Value("${report-server.base-uri:http://report-server}")
    String reportServerURI;

    private static final String DEFAULT_PROVIDER = "OpenLoadFlow";

    @Autowired
    private NetworkStoreService networkStoreService;
    private ObjectMapper objectMapper;

    public LoadFlowService() {
        objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.registerModule(new ReporterModelJsonModule());
        objectMapper.setInjectableValues(new InjectableValues.Std().addValue(ReporterModelDeserializer.DICTIONARY_VALUE_ID, null));

    }

    private Network getNetwork(UUID networkUuid, String variantId) {
        try {
            Network network = networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
            if (variantId != null) {
                network.getVariantManager().setWorkingVariant(variantId);
            }
            return network;
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    LoadFlowResult loadFlow(UUID networkUuid, String variantId, List<UUID> otherNetworksUuid, LoadFlowParameters parameters,
                            String provider, ReportInfos reportInfos) {
        LoadFlowParameters params = parameters != null ? parameters : new LoadFlowParameters();
        LoadFlowResult result;

        Reporter reporter;
        if (reportInfos.getReportId() != null) {
            String name = reportInfos.getReportName() == null ? "loadflow" : reportInfos.getReportName();
            reporter = new ReporterModel(name, name);
        } else {
            reporter = Reporter.NO_OP;
        }
        LoadFlow.Runner runner = LoadFlow.find(provider != null ? provider : DEFAULT_PROVIDER);

        if (otherNetworksUuid.isEmpty()) {
            Network network = getNetwork(networkUuid, variantId);

            // launch the load flow on the network
            result = runner.run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), params, reporter);
            // flush network in the network store
            if (result.isOk()) {
                networkStoreService.flush(network);
            }
        } else {
            // creation of the merging view and merging the networks
            MergingView mergingView = MergingView.create("merged", "iidm");
            List<Network> networks = new ArrayList<>();
            networks.add(getNetwork(networkUuid, variantId));
            otherNetworksUuid.forEach(uuid -> networks.add(getNetwork(uuid, variantId)));
            mergingView.merge(networks.toArray(new Network[networks.size()]));

            // launch the load flow on the merging view
            result = runner.run(mergingView, mergingView.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(), params, reporter);
            if (result.isOk()) {
                // flush each network of the merging view in the network store
                networks.forEach(network -> networkStoreService.flush(network));
            }
        }
        if (reportInfos.getReportId() != null) {
            sendReport(reportInfos.getReportId(), reporter, reportInfos.getOverwriteReport());
        }

        return result;
    }

    private void sendReport(UUID reportId, Reporter reporter, boolean overwrite) {
        var restTemplate = new RestTemplate();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resourceUrl = reportServerURI + DELIMITER + REPORT_API_VERSION + DELIMITER + "reports" + DELIMITER + reportId.toString();
        var uriBuilder = UriComponentsBuilder.fromHttpUrl(resourceUrl).queryParam("overwrite", overwrite);
        try {
            restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(reporter), headers), ReporterModel.class);
        } catch (JsonProcessingException error) {
            throw new PowsyblException("error creating report", error);
        }
    }
}
