/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.NestedServletException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.gridsuite.loadflow.server.Networks.*;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static final String VARIANT_NOT_FOUND_ID = "variant_notFound";
    private static final UUID TEST_NETWORK_ID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID REPORT_ID = UUID.fromString("7928181c-7977-4592-ba19-aaaaaaaaaaaa");

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    private MockWebServer server;

    @Before
    public void setUp() throws IOException  {
        server = new MockWebServer();
        // Start the server.
        server.start();
        HttpUrl baseHttpUrl = server.url("");
        loadFlowService.reportServerURI = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if (path.matches("/v1/reports/" + REPORT_ID + ".*")) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody("");
                }
                return new MockResponse().setResponseCode(404);

            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    public void test() throws Exception {
        UUID notFoundNetworkId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        given(networkStoreService.getNetwork(TEST_NETWORK_ID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork(false));
        given(networkStoreService.getNetwork(notFoundNetworkId, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willThrow(new PowsyblException());

        // network not existing
        mvc.perform(put("/v1/networks/{networkUuid}/run", notFoundNetworkId))
            .andExpect(status().isNotFound());

        // variant not existing
        Exception exception = assertThrows(NestedServletException.class, () -> mvc.perform(put("/v1/networks/{networkUuid}/run?variantId={variantId}", TEST_NETWORK_ID, VARIANT_NOT_FOUND_ID)));
        assertTrue(exception.getCause().getMessage().contains("Variant '" + VARIANT_NOT_FOUND_ID + "' not found"));

        // load flow without parameters (default parameters are used) on implicit initial variant
        MvcResult result = mvc.perform(put("/v1/networks/{networkUuid}/run", TEST_NETWORK_ID))
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
        String paramsString = stream.toString();

        result = mvc.perform(put("/v1/networks/{networkUuid}/run?variantId={variantId}&reportId={repordId}&reportName=loadflow", TEST_NETWORK_ID, VARIANT_2_ID, REPORT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paramsString))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));
        var requestsDone = getRequestsDone(1);
        assertTrue(requestsDone.contains("/v1/reports/" + REPORT_ID));

        result = mvc.perform(put("/v1/networks/{networkUuid}/run?variantId={variantId}", TEST_NETWORK_ID, VARIANT_3_ID)
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
        given(networkStoreService.getNetwork(TEST_NETWORK_ID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork(true));

        // load flow without parameters (default parameters are used) on failing variant
        MvcResult result = mvc.perform(put("/v1/networks/{networkUuid}/run?variantId={variantId}", TEST_NETWORK_ID, VARIANT_3_ID))
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
            + "&reportId=" + REPORT_ID + "&reportName=report_name";

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
        String paramsString = stream.toString();

        result = mvc.perform(put(url, testNetworkId1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paramsString))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().contains("status\":\"CONVERGED\""));
    }

    @Test
    public void testMergingViewLoadFlowFailWithMultipleVariants() {
        UUID testNetworkId1 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID testNetworkId2 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        UUID testNetworkId3 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

        given(networkStoreService.getNetwork(testNetworkId1, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("1_", true));
        given(networkStoreService.getNetwork(testNetworkId2, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("2_", true));
        given(networkStoreService.getNetwork(testNetworkId3, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("3_", true));

        // load flow without parameters (default parameters are used)
        String url = "/v1/networks/{networkUuid}/run?networkUuid=" + testNetworkId2 + "&networkUuid=" + testNetworkId3
            + "&reportId=" + REPORT_ID + "&reportName=report_name";

        Exception exception = assertThrows(NestedServletException.class, () -> mvc.perform(put(url, testNetworkId1)));
        assertEquals("Merging of multi-variants network is not supported", exception.getCause().getMessage());
    }

    @Test
    public void getProvidersTest() throws Exception {
        mvc.perform(get("/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[\"OpenLoadFlow\",\"Hades2\"]"))
                .andReturn();
    }
}
