/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.MostMeshedSlackBusSelector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test() throws Exception {
        UUID testNetworkId = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID notFoundNetworkId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        given(networkStoreService.getNetwork(testNetworkId, PreloadingStrategy.COLLECTION)).willReturn(createNetwork());
        given(networkStoreService.getNetwork(notFoundNetworkId, PreloadingStrategy.COLLECTION)).willThrow(new PowsyblException());

        // network not existing
        mvc.perform(put("/v1/networks/{networkUuid}/run", notFoundNetworkId))
                .andExpect(status().isNotFound());

        // load flow without parameters (default parameters are used)
        MvcResult result = mvc.perform(put("/v1/networks/{networkUuid}/run", testNetworkId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));

        // load flow with parameters
        LoadFlowParameters params = new LoadFlowParameters()
                .setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        params.addExtension(OpenLoadFlowParameters.class, parametersExt);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonLoadFlowParameters.write(params, stream);
        String paramsString = new String(stream.toByteArray());

        result = mvc.perform(put("/v1/networks/{networkUuid}/run", testNetworkId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paramsString))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));
    }

    @Test
    public void testMergingView() throws Exception {
        UUID testNetworkId1 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID testNetworkId2 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        UUID testNetworkId3 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

        given(networkStoreService.getNetwork(testNetworkId1, PreloadingStrategy.COLLECTION)).willReturn(createNetwork("1_"));
        given(networkStoreService.getNetwork(testNetworkId2, PreloadingStrategy.COLLECTION)).willReturn(createNetwork("2_"));
        given(networkStoreService.getNetwork(testNetworkId3, PreloadingStrategy.COLLECTION)).willReturn(createNetwork("3_"));

        // load flow without parameters (default parameters are used)
        String url = "/v1/networks/{networkUuid}/run?networkUuid=" + testNetworkId2.toString() + "&networkUuid=" + testNetworkId3.toString();

        MvcResult result = mvc.perform(put(url, testNetworkId1))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));

        // load flow with parameters
        LoadFlowParameters params = new LoadFlowParameters()
                .setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);

        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelector(new MostMeshedSlackBusSelector());
        params.addExtension(OpenLoadFlowParameters.class, parametersExt);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonLoadFlowParameters.write(params, stream);
        String paramsString = new String(stream.toByteArray());

        result = mvc.perform(put(url, testNetworkId1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paramsString))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));
    }

    public Network createNetwork() {
        return EurostagTutorialExample1Factory.create();
    }

    public Network createNetwork(String prefix) {
        Network network = NetworkFactory.findDefault().createNetwork(prefix + "network", "test");
        Substation p1 = network.newSubstation()
                .setId(prefix + "P1")
                .setCountry(Country.FR)
                .setTso("RTE")
                .setGeographicalTags("A")
                .add();
        Substation p2 = network.newSubstation()
                .setId(prefix + "P2")
                .setCountry(Country.FR)
                .setTso("RTE")
                .setGeographicalTags("B")
                .add();
        VoltageLevel vlgen = p1.newVoltageLevel()
                .setId(prefix + "VLGEN")
                .setNominalV(24.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vlhv1 = p1.newVoltageLevel()
                .setId(prefix + "VLHV1")
                .setNominalV(380.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vlhv2 = p2.newVoltageLevel()
                .setId(prefix + "VLHV2")
                .setNominalV(380.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vlload = p2.newVoltageLevel()
                .setId(prefix + "VLLOAD")
                .setNominalV(150.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus ngen = vlgen.getBusBreakerView().newBus()
                .setId(prefix + "NGEN")
                .add();
        Bus nhv1 = vlhv1.getBusBreakerView().newBus()
                .setId(prefix + "NHV1")
                .add();
        Bus nhv2 = vlhv2.getBusBreakerView().newBus()
                .setId(prefix + "NHV2")
                .add();
        Bus nload = vlload.getBusBreakerView().newBus()
                .setId(prefix + "NLOAD")
                .add();
        network.newLine()
                .setId(prefix + "NHV1_NHV2_1")
                .setVoltageLevel1(vlhv1.getId())
                .setBus1(nhv1.getId())
                .setConnectableBus1(nhv1.getId())
                .setVoltageLevel2(vlhv2.getId())
                .setBus2(nhv2.getId())
                .setConnectableBus2(nhv2.getId())
                .setR(3.0)
                .setX(33.0)
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
                .setB2(386E-6 / 2)
                .add();
        network.newLine()
                .setId(prefix + "NHV1_NHV2_2")
                .setVoltageLevel1(vlhv1.getId())
                .setBus1(nhv1.getId())
                .setConnectableBus1(nhv1.getId())
                .setVoltageLevel2(vlhv2.getId())
                .setBus2(nhv2.getId())
                .setConnectableBus2(nhv2.getId())
                .setR(3.0)
                .setX(33.0)
                .setG1(0.0)
                .setB1(386E-6 / 2)
                .setG2(0.0)
                .setB2(386E-6 / 2)
                .add();
        int zb380 = 380 * 380 / 100;
        p1.newTwoWindingsTransformer()
                .setId(prefix + "NGEN_NHV1")
                .setVoltageLevel1(vlgen.getId())
                .setBus1(ngen.getId())
                .setConnectableBus1(ngen.getId())
                .setRatedU1(24.0)
                .setVoltageLevel2(vlhv1.getId())
                .setBus2(nhv1.getId())
                .setConnectableBus2(nhv1.getId())
                .setRatedU2(400.0)
                .setR(0.24 / 1300 * zb380)
                .setX(Math.sqrt(10 * 10 - 0.24 * 0.24) / 1300 * zb380)
                .setG(0.0)
                .setB(0.0)
                .add();
        int zb150 = 150 * 150 / 100;
        TwoWindingsTransformer nhv2Nload = p2.newTwoWindingsTransformer()
                .setId(prefix + "NHV2_NLOAD")
                .setVoltageLevel1(vlhv2.getId())
                .setBus1(nhv2.getId())
                .setConnectableBus1(nhv2.getId())
                .setRatedU1(400.0)
                .setVoltageLevel2(vlload.getId())
                .setBus2(nload.getId())
                .setConnectableBus2(nload.getId())
                .setRatedU2(158.0)
                .setR(0.21 / 1000 * zb150)
                .setX(Math.sqrt(18 * 18 - 0.21 * 0.21) / 1000 * zb150)
                .setG(0.0)
                .setB(0.0)
                .add();
        double a = (158.0 / 150.0) / (400.0 / 380.0);
        nhv2Nload.newRatioTapChanger()
                .beginStep()
                .setRho(0.85f * a)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setRho(a)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .beginStep()
                .setRho(1.15f * a)
                .setR(0.0)
                .setX(0.0)
                .setG(0.0)
                .setB(0.0)
                .endStep()
                .setTapPosition(1)
                .setLoadTapChangingCapabilities(true)
                .setRegulating(true)
                .setTargetV(158.0)
                .setTargetDeadband(0)
                .setRegulationTerminal(nhv2Nload.getTerminal2())
                .add();
        vlload.newLoad()
                .setId(prefix + "LOAD")
                .setBus(nload.getId())
                .setConnectableBus(nload.getId())
                .setP0(600.0)
                .setQ0(200.0)
                .add();
        Generator generator = vlgen.newGenerator()
                .setId(prefix + "GEN")
                .setBus(ngen.getId())
                .setConnectableBus(ngen.getId())
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(24.5)
                .setTargetP(607.0)
                .setTargetQ(301.0)
                .add();
        generator.newMinMaxReactiveLimits()
                .setMinQ(-9999.99)
                .setMaxQ(9999.99)
                .add();
        return network;
    }
}
