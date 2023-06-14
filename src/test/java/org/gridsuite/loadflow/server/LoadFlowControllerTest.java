/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.reporter.Reporter;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.loadflow.server.dto.ComponentResult;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;
import org.gridsuite.loadflow.server.service.NotificationService;
import org.gridsuite.loadflow.server.service.ReportService;
import org.gridsuite.loadflow.server.service.UuidGeneratorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.loadflow.server.service.NotificationService.CANCEL_MESSAGE;
import static org.gridsuite.loadflow.server.service.NotificationService.HEADER_USER_ID;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {LoadFlowApplication.class, TestChannelBinderConfiguration.class})})
public class LoadFlowControllerTest {

    private static final UUID NETWORK_UUID = UUID.fromString("7928181c-7977-4592-ba19-88027e4254e4");
    private static final UUID RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5d");
    private static final UUID OTHER_RESULT_UUID = UUID.fromString("0c8de370-3e6c-4d72-b292-d355a97e0d5a");
    private static final UUID NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("11111111-7977-4592-ba19-88027e4254e4");
    private static final UUID OTHER_NETWORK_FOR_MERGING_VIEW_UUID = UUID.fromString("22222222-7977-4592-ba19-88027e4254e4");
    private static final UUID REPORT_UUID = UUID.fromString("762b7298-8c0f-11ed-a1eb-0242ac120002");

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";

    private static final int TIMEOUT = 1000;

    private static final class LoadFlowResultMock {
        static LoadFlowResult.ComponentResult componentResult1 = new LoadFlowResultImpl.ComponentResultImpl(1, 2, LoadFlowResult.ComponentResult.Status.CONVERGED, 3, "slackBusId1", 4, 5);
        static LoadFlowResult.ComponentResult componentResult2 = new LoadFlowResultImpl.ComponentResultImpl(1, 2, LoadFlowResult.ComponentResult.Status.CONVERGED, 3, "slackBusId1", 4, 5);
        static List<LoadFlowResult.ComponentResult> componentResults = List.of(componentResult1, componentResult2);
        static final LoadFlowResult RESULT = new LoadFlowResultImpl(true, new HashMap<>(), null, componentResults);
    }

    @Autowired
    private OutputDestination output;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private ReportService reportService;

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private UuidGeneratorService uuidGeneratorService;
    private final RestTemplateConfig restTemplateConfig = new RestTemplateConfig();
    @Autowired
    private ObjectMapper mapper;
    private Network network;
    private Network network1;
    private Network networkForMergingView;
    private Network otherNetworkForMergingView;

    private static void assertResultsEquals(LoadFlowResult result, org.gridsuite.loadflow.server.dto.LoadFlowResult resultDto) {
        assertEquals(result.getComponentResults().size(), resultDto.getComponentResults().size());
        List<ComponentResult> componentResultsDto = resultDto.getComponentResults();
        List<LoadFlowResult.ComponentResult> componentResults = result.getComponentResults();

        for (int i = 0; i < componentResultsDto.size(); i++) {
            assertEquals(componentResultsDto.get(i).getConnectedComponentNum(), componentResults.get(i).getConnectedComponentNum());
            assertEquals(componentResultsDto.get(i).getSynchronousComponentNum(), componentResults.get(i).getSynchronousComponentNum());
            assertEquals(componentResultsDto.get(i).getStatus(), componentResults.get(i).getStatus().name());
            assertEquals(componentResultsDto.get(i).getIterationCount(), componentResults.get(i).getIterationCount());
            assertEquals(componentResultsDto.get(i).getSlackBusId(), componentResults.get(i).getSlackBusId());
            assertEquals(componentResultsDto.get(i).getSlackBusActivePowerMismatch(), componentResults.get(i).getSlackBusActivePowerMismatch(), 0.01);
            assertEquals(componentResultsDto.get(i).getDistributedActivePower(), componentResults.get(i).getDistributedActivePower(), 0.01);

        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);

        networkForMergingView = new NetworkFactoryImpl().createNetwork("mergingView", "test");
        given(networkStoreService.getNetwork(NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(networkForMergingView);

        otherNetworkForMergingView = new NetworkFactoryImpl().createNetwork("other", "test 2");
        given(networkStoreService.getNetwork(OTHER_NETWORK_FOR_MERGING_VIEW_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(otherNetworkForMergingView);

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);

        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);

        // purge messages
        while (output.receive(1000, "loadflow.result") != null) {
        }
        // purge messages
        while (output.receive(1000, "loadflow.run") != null) {
        }
        while (output.receive(1000, "loadflow.cancel") != null) {
        }
        while (output.receive(1000, "loadflow.stopped") != null) {
        }
        while (output.receive(1000, "loadflow.failed") != null) {
        }
    }

    @SneakyThrows
    @After
    public void tearDown() {
        mockMvc.perform(delete("/" + VERSION + "/results"))
                .andExpect(status().isOk());
    }

    @Test
    public void runTest() throws Exception {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(LocalComputationManager.getDefault()),
                            any(LoadFlowParameters.class), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(1000, "loadflow.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            org.gridsuite.loadflow.server.dto.LoadFlowResult resultDto = mapper.readValue(result.getResponse().getContentAsString(), org.gridsuite.loadflow.server.dto.LoadFlowResult.class);
            assertResultsEquals(LoadFlowResultMock.RESULT, resultDto);

            // should throw not found if result does not exist
            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", OTHER_RESULT_UUID))
                    .andExpect(status().isNotFound());

            // test one result deletion
            mockMvc.perform(delete("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    public void stopTest() throws Exception {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(LocalComputationManager.getDefault()),
                            any(LoadFlowParameters.class), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            // stop loadlow
            assertNotNull(output.receive(TIMEOUT, "loadflow.run"));
            mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/stop" + "?receiver=me", RESULT_UUID))
                    .andExpect(status().isOk());
            assertNotNull(output.receive(TIMEOUT, "loadflow.cancel"));

            Message<byte[]> message = output.receive(TIMEOUT, "loadflow.stopped");
            assertNotNull(message);
            assertEquals(RESULT_UUID.toString(), message.getHeaders().get("resultUuid"));
            assertEquals("me", message.getHeaders().get("receiver"));
            assertEquals(CANCEL_MESSAGE, message.getHeaders().get("message"));
        }
    }

    @SneakyThrows
    @Test
    public void mergingViewTest() {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(LocalComputationManager.getDefault()),
                            any(LoadFlowParameters.class), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?receiver=me&networkUuid=" + NETWORK_FOR_MERGING_VIEW_UUID, OTHER_NETWORK_FOR_MERGING_VIEW_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));
        }
    }

    @SneakyThrows
    @Test
    public void testStatus() {
        MvcResult result = mockMvc.perform(get(
                        "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("", result.getResponse().getContentAsString());

        mockMvc.perform(put("/" + VERSION + "/results/invalidate-status?resultUuid=" + RESULT_UUID))
                .andExpect(status().isOk());

        result = mockMvc.perform(get(
                        "/" + VERSION + "/results/{resultUuid}/status", RESULT_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals(LoadFlowStatus.NOT_DONE.name(), result.getResponse().getContentAsString());
    }

    @SneakyThrows
    @Test
    public void runWithReportTest() {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(LocalComputationManager.getDefault()),
                            any(LoadFlowParameters.class), any(Reporter.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reporterId=myReporter&receiver=me&reportUuid=" + REPORT_UUID + "&variantId=" + VARIANT_2_ID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "user"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
        }
    }

    @SneakyThrows
    @Test
    public void getProvidersTest() {

        String result = mockMvc.perform(get("/" + VERSION + "/providers")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
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
        String result = mockMvc.perform(get("/" + VERSION + "/default-provider"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8)))
                .andReturn().getResponse().getContentAsString();

        assertTrue(result.contains("OpenLoadFlow"));
    }

    @Test
    public void getSpecificParametersTest() throws Exception {
        // just Hades2
        String result = mockMvc.perform(get("/" + VERSION + "/specific-parameters?provider=Hades2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        Map<String, List<Object>> lfParams = mapper.readValue(result, new TypeReference<>() {
        });
        assertNotNull(lfParams);
        assertEquals(Set.of("Hades2"), lfParams.keySet());
        assertTrue(lfParams.values().stream().noneMatch(l -> CollectionUtils.isEmpty(l)));

        // all providers
        result = mockMvc.perform(get("/" + VERSION + "/specific-parameters"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        lfParams = mapper.readValue(result, new TypeReference<>() {
        });
        assertNotNull(lfParams);
        assertEquals(Set.of("Hades2", "OpenLoadFlow", "DynaFlow"), lfParams.keySet());
        assertTrue(lfParams.values().stream().noneMatch(l -> CollectionUtils.isEmpty(l)));

    }
}
