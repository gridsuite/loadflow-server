/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

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
import java.util.Optional;
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

    @Value("${backing-services.report-server.base-uri:http://report-server}")
    String reportServerURI;

    @Autowired
    private NetworkStoreService networkStoreService;
    private ObjectMapper objectMapper;

    public LoadFlowService() {
        objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.registerModule(new ReporterModelJsonModule());
        objectMapper.setInjectableValues(new InjectableValues.Std().addValue(ReporterModelDeserializer.DICTIONARY_VALUE_ID, null));

    }

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    LoadFlowResult loadFlow(UUID networkUuid, List<UUID> otherNetworksUuid, LoadFlowParameters parameters, Optional<UUID> reportId, Optional<String> reportName) {
        LoadFlowParameters params = parameters != null ? parameters : new LoadFlowParameters();
        LoadFlowResult result;

        Reporter reporter;
        if (otherNetworksUuid.isEmpty()) {
            Network network = getNetwork(networkUuid);

            reporter = new ReporterModel("loadFlow", "loadFlow");
            // launch the load flow on the network
            result = LoadFlow.find().run(network, network.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(),  params, reporter);

            // flush network in the network store
            if (result.isOk()) {
                networkStoreService.flush(network);
            }
            sendReport(networkUuid, reporter, true);
        } else {
            boolean doReport = reportId.isPresent() && reportName.isPresent();
            // creation of the merging view and merging the networks
            MergingView merginvView = MergingView.create("merged", "iidm");
            List<Network> networks = new ArrayList<>();
            networks.add(getNetwork(networkUuid));
            otherNetworksUuid.forEach(uuid -> networks.add(getNetwork(uuid)));
            merginvView.merge(networks.toArray(new Network[0]));

            // launch the load flow on the merging view
            reporter = doReport ? new ReporterModel(reportName.get(), reportName.get()) : Reporter.NO_OP;
            result = LoadFlow.find().run(merginvView, merginvView.getVariantManager().getWorkingVariantId(), LocalComputationManager.getDefault(),  params, reporter);

            if (result.isOk()) {
                // flush each network of the merging view in the network store
                networks.forEach(network -> networkStoreService.flush(network));
            }
            if (doReport) {
                sendReport(reportId.get(), reporter, false);
            }
        }
        return result;
    }

    private void sendReport(UUID networkUuid, Reporter reporter, boolean overwrite) {
        var restTemplate = new RestTemplate();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resourceUrl = reportServerURI + DELIMITER + REPORT_API_VERSION + DELIMITER + "report" + DELIMITER + networkUuid.toString();
        var uriBuilder = UriComponentsBuilder.fromHttpUrl(resourceUrl).queryParam("overwrite", overwrite);
        try {
            restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.PUT, new HttpEntity<>(objectMapper.writeValueAsString(reporter), headers), ReporterModel.class);
        } catch (Exception error) {
            LOGGER.error(error.getMessage());
        }
    }
}
