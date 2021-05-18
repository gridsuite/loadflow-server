/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.mergingview.MergingView;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
class LoadFlowService {

    private static final String DEFAULT_PROVIDER_NAME = "OpenLoadFlow";

    @Autowired
    private NetworkStoreService networkStoreService;

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid, PreloadingStrategy.COLLECTION);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    LoadFlowResult loadFlow(UUID networkUuid, List<UUID> otherNetworksUuid, LoadFlowParameters parameters,
                            String providerName) {
        LoadFlowParameters params = parameters != null ? parameters : new LoadFlowParameters();
        LoadFlowResult result;

        LoadFlow.Runner runner = LoadFlow.find(providerName != null ? providerName : DEFAULT_PROVIDER_NAME);
        if (otherNetworksUuid.isEmpty()) {
            Network network = getNetwork(networkUuid);

            // launch the load flow on the network
            result = runner.run(network, params);
            // flush network in the network store
            if (result.isOk()) {
                networkStoreService.flush(network);
            }

        } else {
            // creation of the merging view and merging the networks
            MergingView merginvView = MergingView.create("merged", "iidm");
            List<Network> networks = new ArrayList<>();
            networks.add(getNetwork(networkUuid));
            otherNetworksUuid.forEach(uuid -> networks.add(getNetwork(uuid)));
            merginvView.merge(networks.toArray(new Network[networks.size()]));

            // launch the load flow on the merging view
            result = runner.run(merginvView, params);
            if (result.isOk()) {
                // flush each network of the merging view in the network store
                networks.forEach(network -> networkStoreService.flush(network));
            }
        }

        return result;
    }
}
