/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.*;
import com.powsybl.network.store.client.NetworkStoreService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@WebMvcTest(LoadFlowController.class)
@ContextConfiguration(classes = {LoadFlowApplication.class})
public class LoadFlowTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    private static final String TEST_NAME = "recollement-auto-20190124-2110-enrichi";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test() throws Exception {
        UUID testNetworkId = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID notFoundNetworkId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        given(networkStoreService.getNetwork(testNetworkId)).willReturn(createNetwork());
        given(networkStoreService.getNetwork(notFoundNetworkId)).willThrow(new PowsyblException());

        // network not existing
        mvc.perform(put("/v1/networks/{networkUuid}", notFoundNetworkId))
                .andExpect(status().isNotFound());

        // load flow launch with success
        mvc.perform(put("/v1/networks/{networkUuid}", testNetworkId))
                .andExpect(status().isOk());
    }

    public Network createNetwork() {
        ReadOnlyDataSource dataSource = new ResourceDataSource(TEST_NAME, new ResourceSet("", TEST_NAME + ".xiidm"));
        return Importers.importData("XIIDM", dataSource, null);
    }
}
