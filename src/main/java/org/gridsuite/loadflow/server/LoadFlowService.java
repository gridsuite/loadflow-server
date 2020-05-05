/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ComponentScan(basePackageClasses = {NetworkStoreService.class})
@Service
class LoadFlowService {

    @Autowired
    private NetworkStoreService networkStoreService;

    private Network getNetwork(UUID networkUuid) {
        try {
            return networkStoreService.getNetwork(networkUuid);
        } catch (PowsyblException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Network '" + networkUuid + "' not found");
        }
    }

    LoadFlowResult loadFlow(UUID networkUuid, LoadFlowParameters parameters) {
        Network network = getNetwork(networkUuid);

        LoadFlowParameters params = parameters;
        if (parameters == null) {
            params = new LoadFlowParameters();
            params.setNoGeneratorReactiveLimits(true);
            OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector())
                .setDistributedSlack(false);
            params.addExtension(OpenLoadFlowParameters.class, parametersExt);
        }

        // launch the load flow on the network
        LoadFlowResult result = LoadFlow.run(network, params);
        if (!result.isOk()) {
            networkStoreService.flush(network);
        }
        return result;
    }
}
