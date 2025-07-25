/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.security.BusBreakerViolationLocation;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.Security;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.gridsuite.computation.dto.GlobalFilter;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.service.*;
import org.gridsuite.filter.AbstractFilter;
import org.gridsuite.filter.identifierlistfilter.IdentifierListFilter;
import org.gridsuite.filter.identifierlistfilter.IdentifierListFilterEquipmentAttributes;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.loadflow.server.dto.Column;
import org.gridsuite.loadflow.server.dto.ComponentResult;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.repositories.GlobalStatusRepository;
import org.gridsuite.loadflow.server.service.LimitReductionService;
import org.gridsuite.loadflow.server.service.LoadFlowParametersService;
import org.gridsuite.loadflow.server.service.LoadFlowWorkerService;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.computation.service.NotificationService.HEADER_USER_ID;
import static org.gridsuite.loadflow.server.service.LoadFlowService.COMPUTATION_TYPE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
    private static final UUID REPORT_UUID = UUID.fromString("762b7298-8c0f-11ed-a1eb-0242ac120002");
    private static final UUID PARAMETERS_UUID = UUID.fromString("762b7298-8c0f-11ed-a1eb-0242ac120003");
    private static final UUID FILTER_ID_1 = UUID.fromString("762b72a8-8c0f-11ed-a1eb-0242ac120003");
    private static final UUID FILTER_ID_2 = UUID.fromString("762b72b8-7c0f-11ed-a1eb-0242ac120003");

    private static final String VARIANT_1_ID = "variant_1";
    private static final String VARIANT_2_ID = "variant_2";
    private static final String VARIANT_3_ID = "variant_3";
    private static final String LF_PROVIDER = "LF_PROVIDER";

    private static final int TIMEOUT = 1000;

    private static final List<LimitViolation> LIMIT_VIOLATIONS_WITH_LOCATION = List.of(
            new LimitViolation("VLGEN", "", LimitViolationType.LOW_VOLTAGE, "limit1", 60, 1500, 0.7F, 1300, ThreeSides.TWO, new BusBreakerViolationLocation(List.of("NHV1"))),
            new LimitViolation("VLGEN", "", LimitViolationType.HIGH_VOLTAGE, "limit2", 300, 900, 0.7F, 1000, ThreeSides.ONE, new BusBreakerViolationLocation(List.of("NHV2"))),
            new LimitViolation("l3", LimitViolationType.CURRENT, "limit3", Integer.MAX_VALUE, 10, 1, 11, TwoSides.ONE));

    @Autowired
    private OutputDestination output;
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private NetworkStoreService networkStoreService;
    @MockBean
    private ReportService reportService;
    @Autowired
    private ExecutionService executionService;
    @SpyBean
    private LoadFlowParametersService loadFlowParametersService;
    @MockBean
    private UuidGeneratorService uuidGeneratorService;
    @Autowired
    private AbstractFilterService filterService;
    @Autowired
    LimitReductionService limitReductionService;
    @Autowired
    private ObjectMapper mapper;
    private Network network;
    private Network network1;
    protected WireMockServer wireMockServer;
    @Autowired
    private GlobalStatusRepository globalStatusRepository;

    private static void assertResultsEquals(LoadFlowResult result, org.gridsuite.loadflow.server.dto.LoadFlowResult resultDto) {
        assertEquals(result.getComponentResults().size(), resultDto.getComponentResults().size());
        List<ComponentResult> componentResultsDto = resultDto.getComponentResults();
        List<LoadFlowResult.ComponentResult> componentResults = result.getComponentResults();

        for (int i = 0; i < componentResultsDto.size(); i++) {
            assertEquals(componentResultsDto.get(i).getConnectedComponentNum(), componentResults.get(i).getConnectedComponentNum());
            assertEquals(componentResultsDto.get(i).getSynchronousComponentNum(), componentResults.get(i).getSynchronousComponentNum());
            assertEquals(componentResultsDto.get(i).getStatus(), componentResults.get(i).getStatus());
            assertEquals(componentResultsDto.get(i).getIterationCount(), componentResults.get(i).getIterationCount());
            assertEquals(componentResultsDto.get(i).getSlackBusResults().size(), componentResults.get(i).getSlackBusResults().size());
            assertEquals(componentResultsDto.get(i).getDistributedActivePower(), componentResults.get(i).getDistributedActivePower(), 0.01);
        }
    }

    private static void assertLimitViolationsEquals(List<LimitViolation> limitViolations, List<LimitViolationInfos> limitViolationsDto, Network network) {
        assertEquals(limitViolations.size(), limitViolationsDto.size());

        for (int i = 0; i < limitViolationsDto.size(); i++) {
            assertEquals(limitViolationsDto.get(i).getSubjectId(), limitViolations.get(i).getSubjectId());
            assertEquals(limitViolationsDto.get(i).getLimit(), limitViolations.get(i).getLimit(), 0.01);
            assertEquals(limitViolationsDto.get(i).getLimitName(), limitViolations.get(i).getLimitName());
            assertEquals(limitViolationsDto.get(i).getValue(), limitViolations.get(i).getValue(), 0.01);
            assertEquals(limitViolationsDto.get(i).getSide(), limitViolations.get(i).getSide() != null ? limitViolations.get(i).getSide().name() : "");
            assertEquals(limitViolationsDto.get(i).getLimitType(), limitViolations.get(i).getLimitType());
            assertEquals(limitViolationsDto.get(i).getActualOverloadDuration(), LoadFlowWorkerService.calculateActualOverloadDuration(LoadFlowWorkerService.toLimitViolationInfos(limitViolations.get(i), network), network));
            assertEquals(limitViolationsDto.get(i).getUpComingOverloadDuration(), LoadFlowWorkerService.calculateUpcomingOverloadDuration(LoadFlowWorkerService.toLimitViolationInfos(limitViolations.get(i), network)));
            assertEquals(limitViolationsDto.get(i).getOverload(), (limitViolations.get(i).getValue() / limitViolations.get(i).getLimit()) * 100, 0.01);
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // network store service mocking
        network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits(new NetworkFactoryImpl());
        Substation substation = network.getSubstation("P2");
        substation.setProperty("whateverPropertyToTestGlobalFilters", "okValue");
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_1_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_3_ID);

        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.ALL_COLLECTIONS_NEEDED_FOR_BUS_VIEW)).willReturn(network);
        given(networkStoreService.getNetwork(NETWORK_UUID, PreloadingStrategy.COLLECTION)).willReturn(network);

        network1 = EurostagTutorialExample1Factory.createWithMoreGenerators(new NetworkFactoryImpl());
        network1.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_2_ID);

        // report service mocking
        doAnswer(i -> null).when(reportService).sendReport(any(), any());

        // UUID service mocking to always generate the same result UUID
        given(uuidGeneratorService.generate()).willReturn(RESULT_UUID);

        // parameters mocking
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setDc(true);
        List<List<Double>> limitReductions = List.of(List.of(1.0, 0.9, 0.8, 0.7), List.of(1.0, 0.9, 0.8, 0.7));

        LoadFlowParametersValues loadFlowParametersValues = LoadFlowParametersValues.builder()
                .provider(limitReductionService.getProviders().iterator().next())
                .commonParameters(loadFlowParameters)
                .specificParameters(Collections.emptyMap())
                .limitReductions(limitReductionService.createLimitReductions(limitReductions))
                .build();
        doReturn(loadFlowParametersValues).when(loadFlowParametersService).getParametersValues(any());

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        ReflectionTestUtils.setField(filterService, "filterServerBaseUri", wireMockServer.baseUrl());
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
        while (output.receive(1000, "loadflow.cancelfailed") != null) {
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
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class);
             MockedStatic<Security> securityMockedStatic = Mockito.mockStatic(Security.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            securityMockedStatic.when(() -> Security.checkLimits(any(), anyDouble())).thenReturn(LimitViolationsMock.limitViolations);
            securityMockedStatic.when(() -> Security.checkLimits(any(), any())).thenReturn(LimitViolationsMock.limitViolations);

            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(executionService.getComputationManager()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID, NETWORK_UUID)
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

            // Should return an empty result with status OK if the result does not exist
            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", OTHER_RESULT_UUID))
                    .andExpect(status().isNotFound());

            // test one result deletion
            mockMvc.perform(delete("/" + VERSION + "/results").queryParam("resultsUuids", RESULT_UUID.toString()))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}", RESULT_UUID))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    public void testGetLimitViolations() throws Exception {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class);
             MockedStatic<Security> securityMockedStatic = Mockito.mockStatic(Security.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            securityMockedStatic.when(() -> Security.checkLimitsDc(any(), any(), anyDouble())).thenReturn(LimitViolationsMock.limitViolations);

            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(executionService.getComputationManager()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID + "&limitReduction=0.7", NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(1000, "loadflow.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            // get loadflow limit violations
            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}/limit-violations?sort=limitName,asc", RESULT_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            List<LimitViolationInfos> limitViolations = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
            });
            assertLimitViolationsEquals(LimitViolationsMock.limitViolations, limitViolations, network);
        }
    }

    @Test
    public void testGetEnumValues() throws Exception {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class);
             MockedStatic<Security> securityMockedStatic = Mockito.mockStatic(Security.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            securityMockedStatic.when(() -> Security.checkLimitsDc(any(), any(), anyDouble())).thenReturn(LimitViolationsMock.limitViolations);

            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(executionService.getComputationManager()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID + "&limitReduction=0.7", NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(1000, "loadflow.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            // get loadflow limit types
            MvcResult mvcResult = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}/limit-types", RESULT_UUID))
                    .andExpectAll(
                            status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON)
                    ).andReturn();
            List<LimitViolationType> limitTypes = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
            });
            assertEquals(0, limitTypes.size());

            // get loadflow branch sides
            mvcResult = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}/branch-sides", RESULT_UUID))
                    .andExpectAll(
                            status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON)
                    ).andReturn();
            List<TwoSides> sides = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
            });
            assertEquals(2, sides.size());
            assertTrue(sides.contains(TwoSides.ONE));
            assertTrue(sides.contains(TwoSides.TWO));

            // get loadflow computing status
            mvcResult = mockMvc.perform(get("/" + VERSION + "/results/{resultUuid}/computation-status", RESULT_UUID))
                    .andExpectAll(
                            status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON)
                    ).andReturn();
            List<LoadFlowResult.ComponentResult.Status> status = mapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() {
            });
            assertEquals(1, status.size());
            assertTrue(status.contains(LoadFlowResult.ComponentResult.Status.CONVERGED));
        }
    }

    @Test
    public void testGetLimitViolationsWithFilters() throws Exception {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class);
             MockedStatic<Security> securityMockedStatic = Mockito.mockStatic(Security.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            securityMockedStatic.when(() -> Security.checkLimitsDc(any(), any(), anyDouble())).thenReturn(LimitViolationsMock.limitViolations);

            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(executionService.getComputationManager()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
            loadFlowParameters.setDc(true);
            LoadFlowParametersValues loadFlowParametersInfos = LoadFlowParametersValues.builder()
                    .commonParameters(loadFlowParameters)
                    .specificParameters(Collections.emptyMap())
                    .build();
            doReturn(Optional.of(loadFlowParametersInfos)).when(loadFlowParametersService).getParametersValues(any(), any());

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID + "&limitReduction=0.7", NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(1000, "loadflow.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            // get loadflow limit violations with filter
            MvcResult mvcResult = mockMvc.perform(get("/" + VERSION + "/results/" + RESULT_UUID + "/limit-violations?" + buildFilterUrl(false)))
                    .andExpectAll(
                            status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON)
                    ).andReturn();
            String resultAsString = mvcResult.getResponse().getContentAsString();
            List<LimitViolationInfos> limitViolationInfos = mapper.readValue(resultAsString, new TypeReference<>() {
            });
            assertEquals(1, limitViolationInfos.size());

            // get loadflow component result with tolerated filter
            mvcResult = mockMvc.perform(get("/" + VERSION + "/results/" + RESULT_UUID + "/limit-violations?" + buildFilterUrlWithTolerance(false)))
                    .andExpectAll(
                            status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON)
                    ).andReturn();
            resultAsString = mvcResult.getResponse().getContentAsString();
            limitViolationInfos = mapper.readValue(resultAsString, new TypeReference<>() {
            });
            assertEquals(1, limitViolationInfos.size());

        }

    }

    @Test
    public void testGetLimitViolationsWithGlobalFilters() throws Exception {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class);
             MockedStatic<Security> securityMockedStatic = Mockito.mockStatic(Security.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            securityMockedStatic.when(() -> Security.checkLimitsDc(any(), any(), anyDouble())).thenReturn(LimitViolationsMock.limitViolations);

            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(executionService.getComputationManager()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
            loadFlowParameters.setDc(true);
            LoadFlowParametersValues loadFlowParametersInfos = LoadFlowParametersValues.builder()
                    .commonParameters(loadFlowParameters)
                    .specificParameters(Collections.emptyMap())
                    .build();
            doReturn(Optional.of(loadFlowParametersInfos)).when(loadFlowParametersService).getParametersValues(any(), any());

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&sort=limitName,asc&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID + "&limitReduction=0.7", NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(1000, "loadflow.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            // get limit violations with filters and different globalFilters
            assertLimitViolations(createStringGlobalFilter(
                    List.of("380", "150"),
                    Map.of(),
                    List.of(Country.FR, Country.IT),
                    List.of(),
                    List.of(LimitViolationType.CURRENT)
            ), 4);
            assertLimitViolations(createStringGlobalFilter(
                    List.of("24"),
                    Map.of(),
                    List.of(Country.FR, Country.IT),
                    List.of(),
                    List.of(LimitViolationType.HIGH_VOLTAGE, LimitViolationType.LOW_VOLTAGE)
            ), 0);
            assertLimitViolations(createStringGlobalFilter(
                    List.of("380"),
                    Map.of(),
                    List.of(),
                    List.of(),
                    List.of(LimitViolationType.CURRENT)
            ), 4);
            assertLimitViolations(createStringGlobalFilter(
                    List.of(),
                    Map.of(),
                    List.of(Country.FR),
                    List.of(),
                    List.of(LimitViolationType.CURRENT)
            ), 4);
            assertLimitViolations(createStringGlobalFilter(
                    List.of(),
                    Map.of("whateverPropertyToTestGlobalFilters", List.of("okValue")),
                    List.of(Country.FR),
                    List.of(),
                    List.of(LimitViolationType.CURRENT)
            ), 4);
            assertLimitViolations(createStringGlobalFilter(
                    null,
                    Map.of("whateverPropertyToTestGlobalFilters", List.of("badValue")),
                    null,
                    null,
                    List.of(LimitViolationType.CURRENT)
            ), 0);

            // generic filter with line filter
            AbstractFilter lineFilter = new IdentifierListFilter(
                    FILTER_ID_1,
                    new Date(),
                    EquipmentType.LINE,
                    List.of(new IdentifierListFilterEquipmentAttributes("NHV1_NHV2_1", 30.)));

            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/v1/filters/metadata\\?ids=" + FILTER_ID_1))
                    .willReturn(WireMock.ok()
                            .withBody(mapper.writeValueAsString(List.of(lineFilter)))
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))).getId();

            assertLimitViolations(createStringGlobalFilter(
                    List.of(),
                    Map.of(),
                    List.of(Country.FR),
                    List.of(FILTER_ID_1),
                    List.of(LimitViolationType.CURRENT)
            ), 2);

            // generic filter with voltage level filter
            AbstractFilter votageLevelFilter = new IdentifierListFilter(
                FILTER_ID_2,
                new Date(),
                EquipmentType.VOLTAGE_LEVEL,
                List.of(new IdentifierListFilterEquipmentAttributes("VLHV1", 30.)));

            wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/v1/filters/metadata\\?ids=" + FILTER_ID_2))
                .willReturn(WireMock.ok()
                    .withBody(mapper.writeValueAsString(List.of(votageLevelFilter)))
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))).getId();

            assertLimitViolations(createStringGlobalFilter(
                    List.of(),
                    Map.of(),
                    List.of(Country.FR),
                    List.of(FILTER_ID_2),
                    List.of(LimitViolationType.CURRENT)
            ), 4);
        }
    }

    private String createStringGlobalFilter(
            List<String> nominalVs,
            Map<String, List<String>> substationProperty,
            List<Country> countryCodes,
            List<UUID> genericFiltersUuid,
            List<LimitViolationType> limitViolationTypes
    ) throws JsonProcessingException {
        GlobalFilter globalFilter = GlobalFilter.builder()
                .nominalV(nominalVs)
                .substationProperty(substationProperty)
                .countryCode(countryCodes)
                .genericFilter(genericFiltersUuid)
                .limitViolationsTypes(limitViolationTypes)
                .build();
        return new ObjectMapper().writeValueAsString(globalFilter);
    }

    private String buildGlobalFilterUrl(String stringGlobalFilter) throws JsonProcessingException {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath("/" + VERSION + "/results/" + RESULT_UUID + "/limit-violations");

        // Creating a list of resource filters
        List<ResourceFilterDTO> resourceFilters = List.of(
                new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.EQUALS, new String[]{"CURRENT"}, Column.LIMIT_TYPE.columnName()));
        String stringFilters = new ObjectMapper().writeValueAsString(resourceFilters);

        if (!StringUtils.isEmpty(stringFilters)) {
            String encodedFilters = URLEncoder.encode(stringFilters, StandardCharsets.UTF_8);
            uriComponentsBuilder.queryParam("filters", encodedFilters);
        }

        if (!StringUtils.isEmpty(stringGlobalFilter)) {
            String encodedGlobalFilters = URLEncoder.encode(stringGlobalFilter, StandardCharsets.UTF_8);
            uriComponentsBuilder.queryParam("globalFilters", encodedGlobalFilters);
            uriComponentsBuilder.queryParam("networkUuid", NETWORK_UUID);
            uriComponentsBuilder.queryParam("variantId", VARIANT_2_ID);
        }

        return uriComponentsBuilder.build().toUriString();
    }

    private void assertLimitViolations(String stringGlobalFilter, int expectedCount) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get(buildGlobalFilterUrl(stringGlobalFilter)))
                .andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON)
                )
                .andReturn();
        String limitViolationInfosJson = mvcResult.getResponse().getContentAsString();
        List<LimitViolationInfos> limitViolationInfos = mapper.readValue(limitViolationInfosJson, new TypeReference<>() {
        });
        assertEquals(expectedCount, limitViolationInfos.size());
    }

    @Test
    public void testComponentResultWithFilters() throws Exception {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class);
             MockedStatic<Security> securityMockedStatic = Mockito.mockStatic(Security.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            securityMockedStatic.when(() -> Security.checkLimitsDc(any(), anyDouble(), anyDouble())).thenReturn(LimitViolationsMock.limitViolations);

            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(executionService.getComputationManager()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
            loadFlowParameters.setDc(true);
            LoadFlowParametersValues loadFlowParametersInfos = LoadFlowParametersValues.builder()
                    .commonParameters(loadFlowParameters)
                    .specificParameters(Collections.emptyMap())
                    .build();
            doReturn(Optional.of(loadFlowParametersInfos)).when(loadFlowParametersService).getParametersValues(any(), any());

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID + "&limitReduction=0.7", NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(1000, "loadflow.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            // get loadflow component result with filter
            MvcResult mvcResult = mockMvc.perform(get("/" + VERSION + "/results/" + RESULT_UUID + "?" + buildFilterUrl(true)))
                    .andExpectAll(
                            status().isOk(),
                            content().contentType(MediaType.APPLICATION_JSON)
                    ).andReturn();
            String resultAsString = mvcResult.getResponse().getContentAsString();
            org.gridsuite.loadflow.server.dto.LoadFlowResult resultDto = mapper.readValue(resultAsString, org.gridsuite.loadflow.server.dto.LoadFlowResult.class);
            assertResultsEquals(LoadFlowResultMock.RESULT, resultDto);

        }

    }

    private String buildFilterUrl(boolean hasChildFilter) {
        String filterUrl = "";
        try {
            List<ResourceFilterDTO> filters = List.of(new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.STARTS_WITH, "NHV1_NHV2", Column.SUBJECT_ID.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.EQUALS, new String[]{"CURRENT"}, Column.LIMIT_TYPE.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.GREATER_THAN_OR_EQUAL, "1500", Column.LIMIT.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.LESS_THAN_OR_EQUAL, "1200", Column.VALUE.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.NOT_EQUAL, "2", Column.UP_COMING_OVERLOAD.columnName())
            );
            List<ResourceFilterDTO> childFilters = List.of(
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.GREATER_THAN_OR_EQUAL, "3",
                            ComponentResultEntity.Fields.slackBusResults + "." + Column.ACTIVE_POWER_MISMATCH.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.STARTS_WITH, "slackBusId1",
                            ComponentResultEntity.Fields.slackBusResults + "." + Column.ID.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.GREATER_THAN_OR_EQUAL, "3", Column.ITERATION_COUNT.columnName())
            );

            String jsonFilters = new ObjectMapper().writeValueAsString(hasChildFilter ? childFilters : filters);

            filterUrl = "filters=" + URLEncoder.encode(jsonFilters, StandardCharsets.UTF_8);

            return filterUrl;
        } catch (Exception ignored) {
        }
        return filterUrl;
    }

    private String buildFilterUrlWithTolerance(boolean hasChildFilter) {
        String filterUrl = "";
        try {
            List<ResourceFilterDTO> filters = List.of(new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.STARTS_WITH, "NHV1_NHV2", Column.SUBJECT_ID.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.EQUALS, new String[]{"CURRENT"}, Column.LIMIT_TYPE.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.CONTAINS, new String[]{"limit1", "limit2"}, Column.LIMIT_NAME.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.GREATER_THAN_OR_EQUAL, "1499.99999", Column.LIMIT.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.LESS_THAN_OR_EQUAL, "1200.00001", Column.VALUE.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.NOT_EQUAL, "66.66665", Column.OVERLOAD.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.NOT_EQUAL, "2", Column.UP_COMING_OVERLOAD.columnName())
            );
            List<ResourceFilterDTO> childFilters = List.of(
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.GREATER_THAN_OR_EQUAL, "3",
                            ComponentResultEntity.Fields.slackBusResults + "." + Column.ACTIVE_POWER_MISMATCH.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.TEXT, ResourceFilterDTO.Type.STARTS_WITH, "slackBusId1",
                            ComponentResultEntity.Fields.slackBusResults + "." + Column.ID.columnName()),
                    new ResourceFilterDTO(ResourceFilterDTO.DataType.NUMBER, ResourceFilterDTO.Type.GREATER_THAN_OR_EQUAL, "3", Column.ITERATION_COUNT.columnName())
            );

            String jsonFilters = new ObjectMapper().writeValueAsString(hasChildFilter ? childFilters : filters);

            filterUrl = "filters=" + URLEncoder.encode(jsonFilters, StandardCharsets.UTF_8);

            return filterUrl;
        } catch (Exception ignored) {
        }
        return filterUrl;
    }

    @Test
    public void testDeleteResults() throws Exception {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(executionService.getComputationManager()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID, NETWORK_UUID)
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

            // test result deletion
            mockMvc.perform(delete("/" + VERSION + "/results").queryParam("resultsUuids", RESULT_UUID.toString())
            ).andExpect(status().isOk());

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
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();

            // stop loadlow
            assertNotNull(output.receive(TIMEOUT, "loadflow.run"));
            mockMvc.perform(put("/" + VERSION + "/results/{resultUuid}/stop" + "?receiver=me", RESULT_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk());
            assertNotNull(output.receive(TIMEOUT, "loadflow.cancel"));

            Message<byte[]> message = output.receive(TIMEOUT, "loadflow.cancelfailed");
            assertNotNull(message);
            assertEquals(RESULT_UUID.toString(), message.getHeaders().get("resultUuid"));
            assertEquals("me", message.getHeaders().get("receiver"));
            assertEquals(NotificationService.getCancelFailedMessage(COMPUTATION_TYPE), message.getHeaders().get("message"));
            //FIXME how to test the case when the computation is still in progress and we send a cancel request
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
        assertEquals(LoadFlowStatus.NOT_DONE, mapper.readValue(result.getResponse().getContentAsString(), LoadFlowStatus.class));
    }

    @SneakyThrows
    @Test
    public void runWithReportTest() {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(LocalComputationManager.getDefault()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&reporterId=myReporter&receiver=me&reportUuid=" + REPORT_UUID + "&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID, NETWORK_UUID)
                            .header(HEADER_USER_ID, "user"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
        }
    }

    @SneakyThrows
    @Test
    public void runWithDefaultVariant() {
        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(LocalComputationManager.getDefault()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reporterId=myReporter&receiver=me&reportUuid=" + REPORT_UUID + "&parametersUuid=" + PARAMETERS_UUID, NETWORK_UUID)
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
        assertEquals(2, providers.size());
        assertTrue(providers.contains("DynaFlow"));
        assertTrue(providers.contains("OpenLoadFlow"));
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
        // just OpenLoadFlow
        String result = mockMvc.perform(get("/" + VERSION + "/specific-parameters?provider=OpenLoadFlow"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        Map<String, List<Object>> lfParams = mapper.readValue(result, new TypeReference<>() {
        });
        assertNotNull(lfParams);
        assertEquals(Set.of("OpenLoadFlow"), lfParams.keySet());
        assertTrue(lfParams.values().stream().noneMatch(CollectionUtils::isEmpty));

        // all providers
        result = mockMvc.perform(get("/" + VERSION + "/specific-parameters"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        lfParams = mapper.readValue(result, new TypeReference<>() {
        });
        assertNotNull(lfParams);
        assertEquals(Set.of("OpenLoadFlow", "DynaFlow"), lfParams.keySet());
        assertTrue(lfParams.values().stream().noneMatch(CollectionUtils::isEmpty));

    }

    private static final class LoadFlowResultMock {
        static List<LoadFlowResult.SlackBusResult> slackBusResults = List.of(new LoadFlowResultImpl.SlackBusResultImpl("slackBusId1", 4));
        static LoadFlowResult.ComponentResult componentResult1 = new LoadFlowResultImpl.ComponentResultImpl(1, 2, LoadFlowResult.ComponentResult.Status.CONVERGED,
                null, Collections.emptyMap(), 3,
                null, slackBusResults,
                5);
        static LoadFlowResult.ComponentResult componentResult2 = new LoadFlowResultImpl.ComponentResultImpl(1, 2, LoadFlowResult.ComponentResult.Status.CONVERGED,
                null, Collections.emptyMap(), 3,
                null, slackBusResults,
                5);
        static List<LoadFlowResult.ComponentResult> componentResults = List.of(componentResult1, componentResult2);
        static final LoadFlowResult RESULT = new LoadFlowResultImpl(true, new HashMap<>(), null, componentResults);
    }

    private static final class LimitViolationsMock {
        static List<LimitViolation> limitViolations = List.of(
                new LimitViolation("NHV1_NHV2_1", "lineName1", LimitViolationType.CURRENT, "limit1", 60, 1500, 0.7F, 1300, TwoSides.TWO),
                new LimitViolation("NHV1_NHV2_1", "lineName2", LimitViolationType.CURRENT, "limit2", 60, 1500, 0.7F, 1000, TwoSides.TWO),
                new LimitViolation("NHV1_NHV2_2", "lineName3", LimitViolationType.CURRENT, "limit3", 300, 900, 0.7F, 1000, TwoSides.ONE),
                new LimitViolation("NHV1_NHV2_2", "lineName4", LimitViolationType.CURRENT, "limit4", 300, 900, 0.7F, 1000, TwoSides.TWO));
    }

    @Test
    public void testGetLimitViolationsVoltage() throws Exception {
        ((Bus) network.getIdentifiable("NHV1")).setV(380.0).getVoltageLevel().setLowVoltageLimit(400.0).setHighVoltageLimit(450.0);
        ((Bus) network.getIdentifiable("NHV2")).setV(380.0).getVoltageLevel().setLowVoltageLimit(300.0).setHighVoltageLimit(350.0);

        LoadFlow.Runner runner = Mockito.mock(LoadFlow.Runner.class);
        try (MockedStatic<LoadFlow> loadFlowMockedStatic = Mockito.mockStatic(LoadFlow.class);
             MockedStatic<Security> securityMockedStatic = Mockito.mockStatic(Security.class)) {
            loadFlowMockedStatic.when(() -> LoadFlow.find(any())).thenReturn(runner);
            securityMockedStatic.when(() -> Security.checkLimitsDc(any(), any(), anyDouble())).thenReturn(LIMIT_VIOLATIONS_WITH_LOCATION);

            Mockito.when(runner.runAsync(eq(network), eq(VARIANT_2_ID), eq(executionService.getComputationManager()),
                            any(LoadFlowParameters.class), any(ReportNode.class)))
                    .thenReturn(CompletableFuture.completedFuture(LoadFlowResultMock.RESULT));

            MvcResult result = mockMvc.perform(post(
                            "/" + VERSION + "/networks/{networkUuid}/run-and-save?reportType=LoadFlow&receiver=me&variantId=" + VARIANT_2_ID + "&parametersUuid=" + PARAMETERS_UUID + "&limitReduction=0.7", NETWORK_UUID)
                            .header(HEADER_USER_ID, "userId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            assertEquals(RESULT_UUID, mapper.readValue(result.getResponse().getContentAsString(), UUID.class));

            Message<byte[]> resultMessage = output.receive(1000, "loadflow.result");
            assertEquals(RESULT_UUID.toString(), resultMessage.getHeaders().get("resultUuid"));
            assertEquals("me", resultMessage.getHeaders().get("receiver"));

            // get loadflow limit violations
            result = mockMvc.perform(get(
                            "/" + VERSION + "/results/{resultUuid}/limit-violations?sort=locationId,asc", RESULT_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn();
            List<LimitViolationInfos> limitViolations = mapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
            });
            assertEquals(3, limitViolations.size());
            Assertions.assertThat(limitViolations.stream().map(LimitViolationInfos::getLocationId)).hasSameElementsAs(Arrays.asList(null, "VLHV1_0", "VLHV2_0"));
        }
    }

    @Test
    public void testCreateRunningStatus() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/" + VERSION + "/results/running-status"))
            .andExpect(status().isOk())
            .andReturn();

        UUID resultUuid = mapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);

        MvcResult result = mockMvc.perform(get(
                "/" + VERSION + "/results/{resultUuid}/status", resultUuid))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(LoadFlowStatus.RUNNING, mapper.readValue(result.getResponse().getContentAsString(), LoadFlowStatus.class));
        assertEquals(LoadFlowStatus.RUNNING, globalStatusRepository.findByResultUuid(resultUuid).getStatus());
    }

    @Test
    public void testprovider() throws Exception {
        doReturn(LF_PROVIDER).when(loadFlowParametersService).getProvider(any());
        MvcResult result = mockMvc.perform(get(
                        "/" + VERSION + "/parameters/{parametersUuid}/provider", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(LF_PROVIDER, result.getResponse().getContentAsString());
    }
}
