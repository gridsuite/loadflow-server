/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowParameters;
import org.gridsuite.computation.error.ComputationException;
import org.gridsuite.loadflow.server.dto.parameters.LimitReductionsByVoltageLevel;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.dto.parameters.ParameterDifference;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.gridsuite.loadflow.server.repositories.parameters.LoadFlowParametersRepository;
import org.gridsuite.loadflow.server.service.LimitReductionService;
import org.gridsuite.loadflow.server.service.LoadFlowParametersService;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.loadflow.utils.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoadFlowParametersTest {

    private static final String URI_PARAMETERS_BASE = "/v1/parameters";

    private static final String URI_PARAMETERS_GET_PUT = URI_PARAMETERS_BASE + "/";

    private static final String PROVIDER = "LFProvider";

    private static final String PROVIDER_1 = "Provider_1";

    private static final String PROVIDER_2 = "Provider_2";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    LoadFlowParametersRepository parametersRepository;

    @Autowired
    LoadFlowParametersService parametersService;
    @Autowired
    LimitReductionService limitReductionService;

    @Value("${loadflow.default-provider}")
    String defaultLoadFlowProvider;

    @AfterEach
    public void clean() {
        parametersRepository.deleteAll();
    }

    @Test
    void limitReductionConfigTest() {
        List<LimitReductionsByVoltageLevel> limitReductions = limitReductionService.createDefaultLimitReductions();
        assertNotNull(limitReductions);
        assertFalse(limitReductions.isEmpty());

        List<LimitReductionsByVoltageLevel.VoltageLevel> vls = limitReductionService.getVoltageLevels();
        limitReductionService.setVoltageLevels(List.of());
        assertEquals("No configuration for voltage levels", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());
        limitReductionService.setVoltageLevels(vls);

        List<LimitReductionsByVoltageLevel.LimitDuration> lrs = limitReductionService.getLimitDurations();
        limitReductionService.setLimitDurations(List.of());
        assertEquals("No configuration for limit durations", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());
        limitReductionService.setLimitDurations(lrs);

        List<List<Double>> defaultValues = limitReductionService.getDefaultValues();
        limitReductionService.setDefaultValues(List.of());
        assertEquals("No values provided", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of()));
        assertEquals("No values provided", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0)));
        assertEquals("Not enough values provided for voltage levels", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0), List.of(1.0), List.of(1.0)));
        assertEquals("Too many values provided for voltage levels", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0), List.of(1.0)));
        assertEquals("Not enough values provided for limit durations", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0, 1.0, 1.0, 1.0, 1.0), List.of(1.0)));
        assertEquals("Number of values for a voltage level is incorrect", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0, 1.0, 1.0, 1.0, 1.0), List.of(1.0, 1.0, 1.0, 1.0, 1.0)));
        assertEquals("Too many values provided for limit durations", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(2.0, 1.0, 1.0, 1.0), List.of(1.0, 1.0, 1.0, 1.0)));
        assertEquals("Value not between 0 and 1", assertThrows(ComputationException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());
        limitReductionService.setDefaultValues(defaultValues); // restore to avoid side effects
    }

    @Test
    void testCreate() throws Exception {
        LoadFlowParametersInfos parametersToCreate = buildParameters().build();
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);

        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos createdParameters = parametersService.toLoadFlowParametersInfos(parametersRepository.findAll().get(0));

        assertThat(createdParameters).recursivelyEquals(buildParameters()
            .limitReductions(limitReductionService.createDefaultLimitReductions()).build());
    }

    @Test
    void testCreateWithDefaultValues() throws Exception {
        LoadFlowParametersInfos defaultLoadFlowParameters = parametersService.getDefaultParametersValues();
        mockMvc.perform(post(URI_PARAMETERS_BASE + "/default"))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos createdParameters = parametersService.toLoadFlowParametersInfos(parametersRepository.findAll().get(0));

        assertThat(createdParameters).recursivelyEquals(defaultLoadFlowParameters);
    }

    protected void testCreateAndGet(LoadFlowParametersInfos parametersToRead, LoadFlowParametersInfos expectedParameters) throws Exception {
        //create parameters
        MvcResult mvcPostResult = mockMvc.perform(post("/" + VERSION + "/parameters")
                        .content(mapper.writeValueAsString(parametersToRead))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON)
                ).andReturn();

        UUID createdParametersUuid = mapper.readValue(mvcPostResult.getResponse().getContentAsString(), UUID.class);
        assertNotNull(createdParametersUuid);

        // get created parameters
        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + createdParametersUuid))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        LoadFlowParametersInfos receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedParameters).recursivelyEquals(expectedParameters);
    }

    protected void testCreateAndGet(LoadFlowParametersInfos parametersToRead) throws Exception {
        testCreateAndGet(parametersToRead, parametersToRead);
    }

    @Test
    void testRead() throws Exception {
        List<List<Double>> limitReductions = List.of(List.of(1.0, 0.9, 0.8, 0.7), List.of(1.0, 0.9, 0.8, 0.7));

        // Get default limits with no provider
        testCreateAndGet(buildCommonAndSpecificParameters().limitReduction(0.8f).build(),
                buildCommonAndSpecificParameters().limitReduction(0.8f)
                    .limitReductions(limitReductionService.createDefaultLimitReductions()).build());
        // Get default limits with a provider other than 'OpenLoadFlow'
        testCreateAndGet(buildCommonAndSpecificParameters().limitReduction(0.5f).provider(PROVIDER).build(),
                buildCommonAndSpecificParameters().limitReduction(0.5f).provider(PROVIDER)
                    .limitReductions(limitReductionService.createDefaultLimitReductions()).build());
        testCreateAndGet(buildCommonAndSpecificParameters()
                .provider(PROVIDER)
                .limitReductions(limitReductionService.createLimitReductions(limitReductions))
                .build(), buildCommonAndSpecificParameters()
                .provider(PROVIDER)
                .limitReduction(0.8f)
                .limitReductions(limitReductionService.createLimitReductions(limitReductions))
                .build());

        // Get default limits with 'OpenLoadFlow' provider
        String provider = limitReductionService.getProviders().iterator().next();
        testCreateAndGet(buildCommonAndSpecificParameters().provider(provider).limitReductions(List.of()).build(),
                buildCommonAndSpecificParameters()
                .provider(provider)
                .limitReduction(0.8f)
                .limitReductions(limitReductionService.createDefaultLimitReductions())
                .build());
        // Get limits with 'OpenLoadFlow' provider
        testCreateAndGet(buildCommonAndSpecificParameters()
                .provider(provider)
                .limitReductions(limitReductionService.createLimitReductions(limitReductions))
                .build(),
                buildCommonAndSpecificParameters()
                .provider(provider)
                .limitReduction(0.8f)
                .limitReductions(limitReductionService.createLimitReductions(limitReductions))
                .build());

        // Get not existing parameters and expect 404
        mockMvc.perform(get("/" + VERSION + "/parameters/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetDefaultLimitReductions() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/v1/parameters/default-limit-reductions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = mvcResult.getResponse().getContentAsString();
        List<LimitReductionsByVoltageLevel> limitReductions = mapper.readValue(responseContent, new TypeReference<>() { });

        assertNotNull(limitReductions);
        assertFalse(limitReductions.isEmpty());
    }

    @Test
    void testGetDefaultCommonParameters() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/v1/parameters/default-values")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = mvcResult.getResponse().getContentAsString();
        LoadFlowParameters commonParameters = mapper.readValue(responseContent, LoadFlowParameters.class);

        assertNotNull(commonParameters);
        assertEquals(mapper.writeValueAsString(LoadFlowParameters.load()), responseContent);
    }

    @Test
    void testGetDefaultProvider() throws Exception {
        mockMvc.perform(get("/v1/parameters/default-provider"))
                .andExpect(status().isOk())
                .andExpect(content().string(defaultLoadFlowProvider));
    }

    @Test
    void testUpdate() throws Exception {

        LoadFlowParametersInfos parametersToUpdate = buildParameters().build();

        UUID parametersUuid = saveAndReturnId(parametersToUpdate);

        parametersToUpdate = buildParametersUpdate().build();

        String parametersToUpdateJson = mapper.writeValueAsString(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).content(parametersToUpdateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        LoadFlowParametersInfos updatedParameters = parametersService.toLoadFlowParametersInfos(parametersRepository.findById(parametersUuid).get());

        assertThat(updatedParameters).recursivelyEquals(buildParametersUpdate()
            .limitReductions(limitReductionService.createDefaultLimitReductions()).build());
    }

    @Test
    void testResetToDefaultValues() throws Exception {
        List<List<Double>> limitReductionDefaultValues = limitReductionService.getDefaultValues();
        limitReductionService.setDefaultValues(List.of(List.of(1.0, 1.0, 1.0, 1.0), List.of(1.0, 1.0, 1.0, 1.0)));

        LoadFlowParametersInfos defaultValues = parametersService.getDefaultParametersValues();
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        LoadFlowParametersInfos parametersToUpdate = LoadFlowParametersInfos.builder()
                .provider(defaultLoadFlowProvider)
                .commonParameters(loadFlowParameters)
                .specificParametersPerProvider(Map.of())
                .build();
        UUID parametersUuid = saveAndReturnId(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid + "/reset"))
                .andExpect(status().isOk());

        LoadFlowParametersInfos updatedParameters = parametersService.toLoadFlowParametersInfos(parametersRepository.findById(parametersUuid).get());
        assertThat(updatedParameters).recursivelyEquals(defaultValues);
        limitReductionService.setDefaultValues(limitReductionDefaultValues); // restore to avoid side effects
    }

    @Test
    void testUpdateWithoutBody() throws Exception {
        List<List<Double>> limitReductions = List.of(
                List.of(1.0, 0.9, 0.8, 0.7), List.of(1.0, 0.9, 0.8, 0.7));
        List<LimitReductionsByVoltageLevel> limitReductionsValues = limitReductionService.createLimitReductions(limitReductions);
        UUID parametersUuid = saveAndReturnId(buildCommonAndSpecificParameters()
                .limitReduction(0.6f)
                .limitReductions(limitReductionsValues)
                .build());

        // --- an update without body is rejected and no longer resets to default values --- //
        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError());

        // check when resetting, the storedEntity limits values are reset to default values
        LoadFlowParametersEntity storedEntity = parametersRepository.findById(parametersUuid).orElseThrow();
        assertEquals(0.6f, storedEntity.getLimitReduction());
        assertEquals(limitReductions, storedEntity.toLimitReductionsValues());
    }

    @Test
    void testUpdateKeepsOmittedLimitReductions() throws Exception {
        List<List<Double>> reductions = List.of(
                List.of(1.0, 0.9, 0.8, 0.7), List.of(1.0, 0.9, 0.8, 0.7));
        UUID parametersUuid = saveAndReturnId(buildCommonAndSpecificParameters()
                .limitReduction(0.6f)
                .limitReductions(limitReductionService.createLimitReductions(reductions))
                .build());

        // --- test change only a common parameter, limits is not changed --- //
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setDc(true);
        LoadFlowParametersInfos parametersToUpdate = buildCommonAndSpecificParameters()
                .commonParameters(loadFlowParameters)
                .build();
        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid)
                        .content(mapper.writeValueAsString(parametersToUpdate))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // check when updating without limits, the storedEntity values are not changed
        LoadFlowParametersEntity storedEntity = parametersRepository.findById(parametersUuid).orElseThrow();
        assertTrue(storedEntity.isDc());
        assertEquals(Float.valueOf(0.6f), storedEntity.getLimitReduction());
        assertEquals(reductions, storedEntity.toLimitReductionsValues());
    }

    @Test
    void testDelete() throws Exception {

        LoadFlowParametersInfos parametersToDelete = buildParameters().build();

        UUID parametersUuid = saveAndReturnId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();

        List<LoadFlowParametersEntity> storedParameters = parametersRepository.findAll();

        assertThat(storedParameters).isEmpty();
    }

    @Test
    void testDuplicate() throws Exception {
        LoadFlowParametersInfos parametersToDuplicate = buildParameters().build();

        UUID parametersUuid = saveAndReturnId(parametersToDuplicate);

        mockMvc.perform(post(URI_PARAMETERS_BASE + "/{uuid}/duplicate", parametersUuid))
                .andExpect(status().isOk()).andReturn();

        List<LoadFlowParametersEntity> storedParameters = parametersRepository.findAll();

        assertThat(storedParameters).hasSize(2);
    }

    @Test
    void testGetWithInvalidId() throws Exception {
        mockMvc.perform(get(URI_PARAMETERS_GET_PUT + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andReturn();
    }

    @Test
    void testGetParametersValuesForAProvider() throws Exception {
        LoadFlowParametersInfos parameters = buildParameters().build();

        UUID parametersUuid = saveAndReturnId(parameters);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid + "/values" + "?provider=" + PROVIDER))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        LoadFlowParametersValues receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        LoadFlowParametersValues parametersValues = parametersService.toLoadFlowParametersValues(PROVIDER, parameters.toEntity());

        assertThat(receivedParameters).recursivelyEquals(parametersValues);
    }

    @Test
    void testGetParametersValues() {
        LoadFlowParametersInfos parameters = buildParameters().build();

        UUID parametersUuid = saveAndReturnId(parameters);

        LoadFlowParametersValues parametersValues = parametersService.getParametersValues(parametersUuid);

        assertThat(parametersService.toLoadFlowParametersValues(parametersRepository.findById(parametersUuid).get())).recursivelyEquals(parametersValues);
    }

    @Test
    void testgetProvider() throws Exception {
        LoadFlowParametersInfos parameters = buildParameters().build();
        UUID parametersUuid = saveAndReturnId(parameters);

        MvcResult result = mockMvc.perform(get(
                        "/" + VERSION + "/parameters/{parametersUuid}/provider", parametersUuid))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo(parameters.getProvider());
    }

    @Test
    void testGetParametersWithoutDifferences() throws Exception {
        LoadFlowParametersInfos parameters = buildParametersUpdate().build();
        UUID parametersUuid = saveAndReturnId(parameters);

        MvcResult result = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpectAll(
            status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        LoadFlowParametersInfos receivedParameters = mapper.readValue(
            result.getResponse().getContentAsString(),
            LoadFlowParametersInfos.class
        );

        assertThat(receivedParameters).recursivelyEquals(buildParametersUpdate()
            .limitReductions(limitReductionService.createDefaultLimitReductions()).build());
        assertNull(receivedParameters.getParametersDifferences());
    }

    @Test
    void testGetParametersWithDifferencesAgainstDefaultValues() throws Exception {
        LoadFlowParametersInfos parameters = buildParametersUpdate().build();
        UUID parametersUuid = saveAndReturnId(parameters);

        MvcResult result = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid).queryParam("withDifferences", "true"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        LoadFlowParametersInfos receivedParameters = mapper.readValue(result.getResponse().getContentAsString(), LoadFlowParametersInfos.class);

        assertThat(receivedParameters).recursivelyEquals(buildParametersUpdate()
            .limitReductions(limitReductionService.createDefaultLimitReductions())
            .parametersDifferences(Map.of("dc", new ParameterDifference(true, false)))
            .build());
        assertNotNull(receivedParameters.getParametersDifferences());
        assertEquals(new ParameterDifference(true, false), receivedParameters.getParametersDifferences().get("dc"));
    }

    @Test
    void testGetParametersWithDifferencesAgainstReferenceParameters() throws Exception {
        LoadFlowParametersInfos referenceParameters = buildParameters().build();
        UUID referenceParametersUuid = saveAndReturnId(referenceParameters);

        LoadFlowParametersInfos parameters = buildParametersUpdate().build();
        UUID parametersUuid = parametersService.createParameters(parameters);

        MvcResult result = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid).queryParam("withDifferences", "true").queryParam("referenceUuid", referenceParametersUuid.toString()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        LoadFlowParametersInfos receivedParameters = mapper.readValue(result.getResponse().getContentAsString(), LoadFlowParametersInfos.class);

        parameters.setParametersDifferences(Map.of("dc", new ParameterDifference(true, false)));

        assertThat(receivedParameters).recursivelyEquals(buildParametersUpdate()
            .limitReductions(limitReductionService.createDefaultLimitReductions())
            .parametersDifferences(Map.of("dc", new ParameterDifference(true, false)))
            .build());
        assertNotNull(receivedParameters.getParametersDifferences());
        assertEquals(new ParameterDifference(true, false), receivedParameters.getParametersDifferences().get("dc"));
    }

    @Test
    void testGetParametersWithDifferencesAndMissingReferenceUsesDefaultValues() throws Exception {
        LoadFlowParametersInfos parameters = buildParametersUpdate().build();
        UUID parametersUuid = saveAndReturnId(parameters);

        MvcResult result = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid).queryParam("withDifferences", "true").queryParam("referenceUuid", UUID.randomUUID().toString()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        LoadFlowParametersInfos receivedParameters = mapper.readValue(result.getResponse().getContentAsString(), LoadFlowParametersInfos.class);

        parameters.setParametersDifferences(Map.of("dc", new ParameterDifference(true, false)));

        assertNotNull(receivedParameters.getParametersDifferences());
        assertEquals(new ParameterDifference(true, false), receivedParameters.getParametersDifferences().get("dc"));
    }

    @Test
    void testGetParametersWithDifferencesForUnknownParameters() throws Exception {
        mockMvc.perform(get(URI_PARAMETERS_GET_PUT + UUID.randomUUID()).queryParam("withDifferences", "true"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetParametersWithDifferencesForSpecificParameters() throws Exception {
        Map<String, List<com.powsybl.commons.parameters.Parameter>> specificParameters = LoadFlowService.getSpecificLoadFlowParameters(null);

        Map.Entry<String, List<com.powsybl.commons.parameters.Parameter>> providerEntry = specificParameters.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                    .anyMatch(parameter -> parameter.getDefaultValue() != null))
                .findFirst()
                .orElseThrow();

        String provider = providerEntry.getKey();
        com.powsybl.commons.parameters.Parameter referenceParameter = providerEntry.getValue().stream()
            .filter(parameter -> parameter.getDefaultValue() != null)
            .findFirst()
            .orElseThrow();

        String parameterName = referenceParameter.getNames().getFirst();
        String defaultValue = String.valueOf(referenceParameter.getDefaultValue());
        String differentValue = Boolean.toString(!Objects.equals(defaultValue, "true"));

        LoadFlowParametersInfos referenceParameters = buildCommonAndSpecificParameters()
            .provider(provider)
            .limitReduction(0.8f)
            .limitReductions(limitReductionService.createDefaultLimitReductions())
            .specificParametersPerProvider(Map.of(provider, Map.of(parameterName, defaultValue)))
            .build();
        LoadFlowParametersInfos.LoadFlowParametersInfosBuilder parametersBuilder = buildCommonAndSpecificParameters()
                .provider(provider)
                .limitReduction(0.8f)
                .limitReductions(limitReductionService.createDefaultLimitReductions())
                .specificParametersPerProvider(Map.of(provider, Map.of(parameterName, differentValue)));
        LoadFlowParametersInfos parameters = parametersBuilder
            .build();

        UUID referenceParametersUuid = saveAndReturnId(referenceParameters);
        UUID parametersUuid = parametersService.createParameters(parameters);

        MvcResult result = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid).queryParam("withDifferences", "true").queryParam("referenceUuid", referenceParametersUuid.toString()))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        LoadFlowParametersInfos receivedParameters = mapper.readValue(result.getResponse().getContentAsString(), LoadFlowParametersInfos.class);

        assertThat(receivedParameters).recursivelyEquals(parametersBuilder
            .parametersDifferences(Map.of(parameterName, new ParameterDifference(differentValue, defaultValue)))
            .build());
        assertNotNull(receivedParameters.getParametersDifferences());
        assertEquals(new ParameterDifference(differentValue, defaultValue), receivedParameters.getParametersDifferences().get(parameterName));
    }

    // --- test specific parameters --- //

    @Test
    void testKeepsSpecificParamsOfAbsentProvider() throws Exception {
        // initial state: both providers have specific parameters
        Map<String, Map<String, String>> initialSpecific = Map.of(
                PROVIDER_1, Map.of("pro1Param1", "pro1Param1Value1", "pro1Param2", "1.0"),
                PROVIDER_2, Map.of("pro2Param1", "true", "pro2Param2", "false"));
        UUID parametersUuid = saveAndReturnId(buildParametersWithSpecific(initialSpecific).build());

        // update: only PROVIDER_1 is present, PROVIDER_2 is absent
        Map<String, Map<String, String>> updatedSpecific = Map.of(
                PROVIDER_1, Map.of("pro1Param1", "pro1Param1Value2"));
        updateParameters(parametersUuid, buildParametersWithSpecific(updatedSpecific).build());

        Map<String, Map<String, String>> savedSpecificParams = getSavedSpecificParameters(parametersUuid);
        assertEquals(2, savedSpecificParams.size());
        // PROVIDER_1 parameters are fully replaced
        assertEquals(Map.of("pro1Param1", "pro1Param1Value2"), savedSpecificParams.get(PROVIDER_1));
        // PROVIDER_2 parameters are untouched
        assertEquals(Map.of("pro2Param1", "true", "pro2Param2", "false"), savedSpecificParams.get(PROVIDER_2));
    }

    @Test
    void testReplacesAllSpecificParamsOfPresentProvider() throws Exception {
        Map<String, Map<String, String>> initialSpecific = Map.of(
                PROVIDER_2, Map.of("pro2Param1", "true", "pro2Param2", "false", "pro2Param3", "63.0"));
        UUID parametersUuid = saveAndReturnId(buildParametersWithSpecific(initialSpecific).build());

        // "pro2Param1" is changed, "pro2Param2" and "pro2Param3" are removed, "pro2Param4" is added
        Map<String, Map<String, String>> updatedSpecific = Map.of(
                PROVIDER_2, Map.of("pro2Param1", "false", "pro2Param4", "warm"));
        updateParameters(parametersUuid, buildParametersWithSpecific(updatedSpecific).build());

        Map<String, Map<String, String>> savedSpecificParams = getSavedSpecificParameters(parametersUuid);
        assertEquals(Set.of(PROVIDER_2), savedSpecificParams.keySet());
        assertEquals(Map.of("pro2Param1", "false", "pro2Param4", "warm"), savedSpecificParams.get(PROVIDER_2));
    }

    @Test
    void testReplacesSpecificParamsOfAllPresentProviders() throws Exception {
        Map<String, Map<String, String>> initialSpecific = Map.of(
                PROVIDER_1, Map.of("pro1Param1", "pro1Param1Value1", "pro1Param2", "1.0"),
                PROVIDER_2, Map.of("pro2Param1", "true", "pro2Param2", "false"));
        UUID parametersUuid = saveAndReturnId(buildParametersWithSpecific(initialSpecific).build());

        Map<String, Map<String, String>> updatedSpecific = Map.of(
                PROVIDER_1, Map.of("pro1Param2", "2.5"),
                PROVIDER_2, Map.of("pro2Param2", "true"));
        updateParameters(parametersUuid, buildParametersWithSpecific(updatedSpecific).build());

        Map<String, Map<String, String>> savedSpecificParams = getSavedSpecificParameters(parametersUuid);
        assertEquals(updatedSpecific, savedSpecificParams);
    }

    @Test
    void testDeleteSpecificParamsOfPresentProviderWithEmpty() throws Exception {
        Map<String, Map<String, String>> initialSpecific = Map.of(
                PROVIDER_1, Map.of("pro1Param1", "pro1Param1Value1"),
                PROVIDER_2, Map.of("pro2Param1", "true"));
        UUID parametersUuid = saveAndReturnId(buildParametersWithSpecific(initialSpecific).build());

        // PROVIDER_1 is present but with an empty map: all its parameters are deleted, PROVIDER_2 is kept
        Map<String, Map<String, String>> updatedSpecific = Map.of(PROVIDER_1, Map.of());
        updateParameters(parametersUuid, buildParametersWithSpecific(updatedSpecific).build());

        Map<String, Map<String, String>> stored = getSavedSpecificParameters(parametersUuid);
        assertEquals(Set.of(PROVIDER_2), stored.keySet());
        assertEquals(Map.of("pro2Param1", "true"), stored.get(PROVIDER_2));
    }

    @Test
    void testClearsAllProvidersWithNoSpecificParams() throws Exception {
        Map<String, Map<String, String>> initialSpecific = Map.of(
                PROVIDER_1, Map.of("pro1Param1", "pro1Param1Value1"),
                PROVIDER_2, Map.of("pro2Param1", "true"));
        UUID parametersUuid = saveAndReturnId(buildParametersWithSpecific(initialSpecific).build());

        // no provider at all: every existing specific parameter is cleared
        updateParameters(parametersUuid, buildParametersWithSpecific(Map.of()).build());

        assertTrue(getSavedSpecificParameters(parametersUuid).isEmpty());
    }

    // --- test helpers   --- //

    /** Save parameters into the repository and return its UUID. */
    protected UUID saveAndReturnId(LoadFlowParametersInfos parametersInfos) {
        parametersRepository.save(parametersInfos.toEntity());
        return parametersRepository.findAll().get(0).getId();
    }

    protected LoadFlowParametersInfos.LoadFlowParametersInfosBuilder buildCommonAndSpecificParameters() {
        return LoadFlowParametersInfos.builder()
                .commonParameters(LoadFlowParameters.load()).specificParametersPerProvider(Map.of());
    }

    protected LoadFlowParametersInfos.LoadFlowParametersInfosBuilder buildParametersWithProvider(String provider) {
        return buildCommonAndSpecificParameters()
                .provider(provider)
                .limitReduction(0.8f);
    }

    protected LoadFlowParametersInfos.LoadFlowParametersInfosBuilder buildParameters() {
        return buildParametersWithProvider(PROVIDER);
    }

    protected LoadFlowParametersInfos.LoadFlowParametersInfosBuilder buildParametersUpdate() {
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setDc(true);
        return buildParameters()
            .limitReduction(0.5f)
            .commonParameters(loadFlowParameters);
    }

    private LoadFlowParametersInfos.LoadFlowParametersInfosBuilder buildParametersWithSpecific(Map<String, Map<String, String>> specificParametersPerProvider) {
        return buildParameters()
                .specificParametersPerProvider(specificParametersPerProvider);
    }

    private Map<String, Map<String, String>> getSavedSpecificParameters(UUID parametersUuid) {
        return parametersService
                .toLoadFlowParametersInfos(parametersRepository.findById(parametersUuid).orElseThrow())
                .getSpecificParametersPerProvider();
    }

    private void updateParameters(UUID parametersUuid, LoadFlowParametersInfos parameters) throws Exception {
        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid)
                        .content(mapper.writeValueAsString(parameters))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
