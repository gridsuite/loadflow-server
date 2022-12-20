/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
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
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.HttpUrl;
import org.springframework.web.util.NestedServletException;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@WebMvcTest(LoadFlowController.class)
@ContextConfiguration(classes = {LoadFlowApplication.class})
public class LoadFlowTest {

    @Autowired
    private LoadFlowService loadFlowService;
    @Autowired
    private MockMvc mvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    private MockWebServer server;

    private static String VARIANT_1_ID = "variant_1";
    private static String VARIANT_2_ID = "variant_2";
    private static String VARIANT_3_ID = "variant_3";
    private static String VARIANT_NOT_FOUND_ID = "variant_notFound";

    @Before
    public void setUp() throws IOException  {

        MockitoAnnotations.initMocks(this);
        server = new MockWebServer();
        // Start the server.
        server.start();
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        loadFlowService.reportServerURI = baseUrl;
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String path = Objects.requireNonNull(request.getPath());
                if (path.matches("/v1/reports/" + reportId + ".*")) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody("");
                }
                return new MockResponse().setResponseCode(404);

            }
        };
        server.setDispatcher(dispatcher);

    }

    UUID testNetworkId = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    UUID reportId = UUID.fromString("7928181c-7977-4592-ba19-aaaaaaaaaaaa");

    @Test
    public void test() throws Exception {
        UUID notFoundNetworkId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        given(networkStoreService.getNetwork(testNetworkId, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork(false));
        given(networkStoreService.getNetwork(notFoundNetworkId, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willThrow(new PowsyblException());

        // network not existing
        mvc.perform(put("/v1/networks/{networkUuid}/run", notFoundNetworkId))
            .andExpect(status().isNotFound());

        // variant not existing
        Exception exception = assertThrows(NestedServletException.class, () -> mvc.perform(put("/v1/networks/{networkUuid}/run?variantId={variantId}", testNetworkId, VARIANT_NOT_FOUND_ID)));
        assertTrue(exception.getCause().getMessage().contains("Variant '" + VARIANT_NOT_FOUND_ID + "' not found"));

        // load flow without parameters (default parameters are used) on implicit initial variant
        MvcResult result = mvc.perform(put("/v1/networks/{networkUuid}/run", testNetworkId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));

        // load flow with parameters on explicitly given variant
        LoadFlowParameters params = new LoadFlowParameters()
                .setNoGeneratorReactiveLimits(true)
                .setDistributedSlack(false);
        OpenLoadFlowParameters parametersExt = new OpenLoadFlowParameters()
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
        params.addExtension(OpenLoadFlowParameters.class, parametersExt);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonLoadFlowParameters.write(params, stream);
        String paramsString = new String(stream.toByteArray());

        result = mvc.perform(put("/v1/networks/{networkUuid}/run?variantId={variantId}&reportId={repordId}&reportName=loadflow", testNetworkId, VARIANT_2_ID, reportId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paramsString))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));
        var requestsDone = getRequestsDone(1);
        assertTrue(requestsDone.contains("/v1/reports/" + reportId));

        result = mvc.perform(put("/v1/networks/{networkUuid}/run?variantId={variantId}", testNetworkId, VARIANT_3_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(paramsString))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));
        requestsDone = getRequestsDone(1);
        assertTrue(requestsDone.contains(null));
    }

    @Test
    public void testLoadFlowFailingVariant() throws Exception {
        given(networkStoreService.getNetwork(testNetworkId, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork(true));

        // load flow without parameters (default parameters are used) on failing variant
        MvcResult result = mvc.perform(put("/v1/networks/{networkUuid}/run?variantId={variantId}", testNetworkId, VARIANT_3_ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"MAX_ITERATION_REACHED\""));
    }

    private Set<String> getRequestsDone(int n) {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                var res = server.takeRequest(0, TimeUnit.SECONDS);
                if (res != null) {
                    return res.getPath();
                }
                return null;
            } catch (InterruptedException e) {
                //LOGGER.error("Error while attempting to get the request done : ", e);
            }
            return null;
        }).collect(Collectors.toSet());
    }

    @Test
    public void testMergingView() throws Exception {
        UUID testNetworkId1 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID testNetworkId2 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        UUID testNetworkId3 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

        given(networkStoreService.getNetwork(testNetworkId1, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("1_", false));
        given(networkStoreService.getNetwork(testNetworkId2, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("2_", false));
        given(networkStoreService.getNetwork(testNetworkId3, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("3_", false));

        // load flow without parameters (default parameters are used)
        String url = "/v1/networks/{networkUuid}/run?networkUuid=" + testNetworkId2 + "&networkUuid=" + testNetworkId3
            + "&reportId=" + reportId + "&reportName=report_name";

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
                .setSlackBusSelectionMode(SlackBusSelectionMode.MOST_MESHED);
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

    @Test
    public void testMergingViewLoadFlowFailWithMultipleVariants() throws Exception {
        UUID testNetworkId1 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID testNetworkId2 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        UUID testNetworkId3 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

        given(networkStoreService.getNetwork(testNetworkId1, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("1_", true));
        given(networkStoreService.getNetwork(testNetworkId2, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("2_", true));
        given(networkStoreService.getNetwork(testNetworkId3, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("3_", true));

        // load flow without parameters (default parameters are used)
        String url = "/v1/networks/{networkUuid}/run?networkUuid=" + testNetworkId2 + "&networkUuid=" + testNetworkId3
            + "&reportId=" + reportId + "&reportName=report_name";

        Exception exception = assertThrows(NestedServletException.class, () -> mvc.perform(put(url, testNetworkId1)));
        assertEquals("Merging of multi-variants network is not supported", exception.getCause().getMessage());
    }

    public Network createNetwork(boolean withFailingVariant) {
        Network network = EurostagTutorialExample1Factory.create(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        if (withFailingVariant) {
            network.getVariantManager().setWorkingVariant(VARIANT_3_ID);
            VoltageLevel vl = network.getVoltageLevel("VLGEN");
            Bus bus = vl.getBusBreakerView().getBus("NGEN");
            vl.newGenerator()
                .setId("FAILING_GEN")
                .setBus(bus.getId())
                .setConnectableBus(bus.getId())
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(24.5)
                .setTargetP(20000.)
                .setTargetQ(301.0)
                .add();

            network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        }
        return network;
    }

    public Network createNetwork(String prefix, boolean createVariant) {
        Network network = new NetworkFactoryImpl().createNetwork(prefix + "network", "test");
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

        if (createVariant) {
            network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        }

        return network;
    }
}
