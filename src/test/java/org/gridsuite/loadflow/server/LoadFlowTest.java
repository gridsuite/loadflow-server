/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.LoadFlowParametersJsonModule;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.loadflow.server.dto.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.gridsuite.loadflow.server.service.ReportService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.loadflow.server.Networks.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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
    private static final String VERSION = "v1";
    private static final String REPORT_VERSION = "v1";

    @InjectMocks
    private LoadFlowService loadFlowService;

    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private ReportService reportService;

    private MockWebServer server;

    @Autowired
    private WebTestClient webTestClient;

    @Before
    public void setUp() throws IOException {
        mapper.registerModule(new LoadFlowParametersJsonModule());
        objectWriter = mapper.writer().withDefaultPrettyPrinter();
        server = new MockWebServer();
        // Start the server.
        server.start();
        HttpUrl baseHttpUrl = server.url("");
        loadFlowService.reportServerURI = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if (path.matches("/" + REPORT_VERSION + "/reports/" + REPORT_ID + ".*")) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json; charset=utf-8")
                            .setBody("");
                }
                return new MockResponse().setResponseCode(404);

            }
        };
        server.setDispatcher(dispatcher);
    }

    private WebTestClient.ResponseSpec performExchangeException(String url, Object... uriVariables) {
        try {
            return webTestClient.put()
                    .uri(url, uriVariables)
                    .exchange();
        } catch (Exception e) {
            throw e;
        }
    }

    @Test
    public void test() throws Exception {
        UUID notFoundNetworkId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        given(networkStoreService.getNetwork(TEST_NETWORK_ID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork(false));
        given(networkStoreService.getNetwork(notFoundNetworkId, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willThrow(new PowsyblException());
        given(reportService.sendReport(any(UUID.class), any(Reporter.class)))
                .willReturn(Mono.empty());
        // network not existing
        webTestClient.put()
                .uri("/" + VERSION + "/networks/{networkUuid}/run", notFoundNetworkId)
                .exchange()
                .expectStatus().isNotFound();

        // variant not existing
        Exception exception = assertThrows(WebClientRequestException.class, () -> performExchangeException("/" + VERSION + "/networks/{networkUuid}/run?variantId={variantId}", TEST_NETWORK_ID, VARIANT_NOT_FOUND_ID));
        assertTrue(exception.getCause().getMessage().contains("Variant '" + VARIANT_NOT_FOUND_ID + "' not found"));

        // load flow without parameters (default parameters are used) on implicit initial variant
        webTestClient.put()
                .uri("/" + VERSION + "/networks/{networkUuid}/run", TEST_NETWORK_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.componentResults[0].status").isEqualTo("CONVERGED");

        // load flow with parameters on explicitly given variant
        LoadFlowParametersInfos fullParams = LoadFlowParametersInfos.builder()
                .commonParameters(new LoadFlowParameters())
                .specificParameters(Map.of("SlackBusSelectionMode", "MOST_MESHED"))
                .build();
        String paramsString = objectWriter.writeValueAsString(fullParams);

        webTestClient.put()
                .uri("/" + VERSION + "/networks/{networkUuid}/run?variantId={variantId}&reportId={repordId}&reportName=loadflow", TEST_NETWORK_ID, VARIANT_2_ID, REPORT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paramsString)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.componentResults[0].status").isEqualTo("CONVERGED");

        webTestClient.put()
                .uri("/" + VERSION + "/networks/{networkUuid}/run?variantId={variantId}", TEST_NETWORK_ID, VARIANT_3_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paramsString)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.componentResults[0].status").isEqualTo("CONVERGED");

    }

    private void simpleRunWithLFParams(LoadFlowParameters lfParams, Map<String, String> specificParams) throws Exception {
        given(networkStoreService.getNetwork(TEST_NETWORK_ID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork(true));
        LoadFlowParametersInfos fullParams = LoadFlowParametersInfos.builder()
                .commonParameters(lfParams)
                .specificParameters(specificParams)
                .build();
        String paramsString = objectWriter.writeValueAsString(fullParams);

        webTestClient.put()
                .uri("/" + VERSION + "/networks/{networkUuid}/run?variantId={variantId}", TEST_NETWORK_ID, VARIANT_3_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paramsString)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.componentResults[0].status").isEqualTo("MAX_ITERATION_REACHED");
    }

    @Test
    public void testLoadFlowWithLFParams() throws Exception {
        simpleRunWithLFParams(null, null);
        LoadFlowParameters lfParams = new LoadFlowParameters();
        simpleRunWithLFParams(lfParams, null);
        simpleRunWithLFParams(lfParams, Map.of());
        simpleRunWithLFParams(lfParams, Map.of("reactiveRangeCheckMode", "TARGET_P"));
    }

    @Test
    public void testLoadFlowFailingVariant() throws Exception {
        given(networkStoreService.getNetwork(TEST_NETWORK_ID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork(true));

        // load flow without parameters (default parameters are used) on failing variant
        webTestClient.put()
                .uri("/" + VERSION + "/networks/{networkUuid}/run?variantId={variantId}", TEST_NETWORK_ID, VARIANT_3_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.componentResults[0].status").isEqualTo("MAX_ITERATION_REACHED");
    }

    @Test
    public void testMergingView() throws Exception {
        UUID testNetworkId1 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
        UUID testNetworkId2 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e5");
        UUID testNetworkId3 = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e6");

        given(networkStoreService.getNetwork(testNetworkId1, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("1_", false));
        given(networkStoreService.getNetwork(testNetworkId2, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("2_", false));
        given(networkStoreService.getNetwork(testNetworkId3, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(createNetwork("3_", false));
        given(reportService.sendReport(any(UUID.class), any(Reporter.class)))
                .willReturn(Mono.empty());
        // load flow without parameters (default parameters are used)
        String url = "/" + VERSION + "/networks/{networkUuid}/run?networkUuid=" + testNetworkId2 + "&networkUuid=" + testNetworkId3
                + "&reportId=" + REPORT_ID + "&reportName=report_name";

        webTestClient.put()
                .uri(url, testNetworkId1)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.componentResults[0].status").isEqualTo("CONVERGED");

        // load flow with parameters
        LoadFlowParametersInfos fullParams = LoadFlowParametersInfos.builder()
                .commonParameters(new LoadFlowParameters())
                .specificParameters(Map.of("SlackBusSelectionMode", "MOST_MESHED"))
                .build();
        String paramsString = objectWriter.writeValueAsString(fullParams);

        webTestClient.put()
                .uri(url, testNetworkId1)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(paramsString)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.componentResults[0].status").isEqualTo("CONVERGED");
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
        String url = "/" + VERSION + "/networks/{networkUuid}/run?networkUuid=" + testNetworkId2 + "&networkUuid=" + testNetworkId3
                + "&reportId=" + REPORT_ID + "&reportName=report_name";

        Exception exception = assertThrows(WebClientRequestException.class, () -> performExchangeException(url, testNetworkId1));
        assertTrue(exception.getCause().getMessage().contains("Merging of multi-variants network is not supported"));
    }

    @Test
    public void getProvidersTest() throws Exception {
        String result = webTestClient.get()
                .uri("/" + VERSION + "/providers")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        List<String> providers = mapper.readValue(result, new TypeReference<>() {
        });

        assertNotNull(providers);
        assertEquals(3, providers.size());
        assertTrue(providers.contains("DynaFlow"));
        assertTrue(providers.contains("OpenLoadFlow"));
        assertTrue(providers.contains("Hades2"));
    }

    @Test
    public void getDefaultProviderTest() throws Exception {

        webTestClient.get()
                .uri("/" + VERSION + "/default-provider")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .expectBody(String.class)
                .isEqualTo("OpenLoadFlow");
    }

    @Test
    public void getSpecificParametersTest() throws Exception {
        // just Hades2
        String result = webTestClient.get()
                .uri("/" + VERSION + "/specific-parameters?provider=Hades2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        Map<String, List<Object>> lfParams = mapper.readValue(result, new TypeReference<>() {
        });
        assertNotNull(lfParams);
        assertEquals(Set.of("Hades2"), lfParams.keySet());
        assertTrue(lfParams.values().stream().noneMatch(l -> CollectionUtils.isEmpty(l)));

        // all providers
        result = webTestClient.get()
                .uri("/" + VERSION + "/specific-parameters")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

        lfParams = mapper.readValue(result, new TypeReference<>() {
        });
        assertNotNull(lfParams);
        assertEquals(Set.of("Hades2", "OpenLoadFlow", "DynaFlow"), lfParams.keySet());
        assertTrue(lfParams.values().stream().noneMatch(l -> CollectionUtils.isEmpty(l)));

    }
}
