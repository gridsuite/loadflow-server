/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import static com.powsybl.network.store.model.NetworkStoreApi.VERSION;
import static org.gridsuite.loadflow.utils.assertions.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gridsuite.loadflow.server.dto.parameters.LimitReductionsByVoltageLevel;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.gridsuite.loadflow.server.repositories.parameters.LoadFlowParametersRepository;
import org.gridsuite.loadflow.server.service.LimitReductionService;
import org.gridsuite.loadflow.server.service.LoadFlowParametersService;
import org.gridsuite.loadflow.server.utils.LoadflowException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;

import static org.junit.Assert.*;
import static org.junit.Assert.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowParameters;

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
        assertEquals("No configuration for voltage levels", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());
        limitReductionService.setVoltageLevels(vls);

        List<LimitReductionsByVoltageLevel.LimitDuration> lrs = limitReductionService.getLimitDurations();
        limitReductionService.setLimitDurations(List.of());
        assertEquals("No configuration for limit durations", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());
        limitReductionService.setLimitDurations(lrs);

        limitReductionService.setDefaultValues(List.of());
        assertEquals("No values provided", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of()));
        assertEquals("No values provided", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0)));
        assertEquals("Not enough values provided for voltage levels", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0), List.of(1.0), List.of(1.0)));
        assertEquals("Too many values provided for voltage levels", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0), List.of(1.0)));
        assertEquals("Not enough values provided for limit durations", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0, 1.0, 1.0, 1.0, 1.0), List.of(1.0)));
        assertEquals("Number of values for a voltage level is incorrect", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(1.0, 1.0, 1.0, 1.0, 1.0), List.of(1.0, 1.0, 1.0, 1.0, 1.0)));
        assertEquals("Too many values provided for limit durations", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());

        limitReductionService.setDefaultValues(List.of(List.of(2.0, 1.0, 1.0, 1.0), List.of(1.0, 1.0, 1.0, 1.0)));
        assertEquals("Value not between 0 and 1", assertThrows(LoadflowException.class, () -> limitReductionService.createDefaultLimitReductions()).getMessage());
    }

    @Test
    void testCreate() throws Exception {
        LoadFlowParametersInfos parametersToCreate = buildParameters();
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);

        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos createdParameters = parametersService.toLoadFlowParametersInfos(parametersRepository.findAll().get(0));

        assertThat(createdParameters).recursivelyEquals(parametersToCreate);
    }

    @Test
    void testCreateWithDefaultValues() throws Exception {
        LoadFlowParametersInfos defaultLoadFlowParameters = parametersService.getDefaultParametersValues(defaultLoadFlowProvider);
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

        // Get no limits with no provider
        testCreateAndGet(buildCommonAndSpecificParameters().build());
        // Get no limits with a provider other than 'OpenLoadFlow'
        testCreateAndGet(buildCommonAndSpecificParameters().provider(PROVIDER).build());
        testCreateAndGet(buildCommonAndSpecificParameters()
                .provider(PROVIDER)
                .limitReductions(limitReductionService.createLimitReductions(limitReductions))
                .build(), buildCommonAndSpecificParameters()
                .provider(PROVIDER)
                .limitReductions(null)
                .build());

        // Get default limits with 'OpenLoadFlow' provider
        String defaultProvider = limitReductionService.getProviders().iterator().next();
        testCreateAndGet(buildCommonAndSpecificParameters().provider(defaultProvider).limitReductions(List.of()).build(), buildCommonAndSpecificParameters()
                .provider(defaultProvider)
                .limitReductions(limitReductionService.createDefaultLimitReductions())
                .build());
        // Get limits with 'OpenLoadFlow' provider
        testCreateAndGet(buildCommonAndSpecificParameters()
                .provider(defaultProvider)
                .limitReductions(limitReductionService.createLimitReductions(limitReductions))
                .build());

        // Get not existing parameters and expect 404
        mockMvc.perform(get("/" + VERSION + "/parameters/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdate() throws Exception {

        LoadFlowParametersInfos parametersToUpdate = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToUpdate);

        parametersToUpdate = buildParametersUpdate();

        String parametersToUpdateJson = mapper.writeValueAsString(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).content(parametersToUpdateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        LoadFlowParametersInfos updatedParameters = parametersService.toLoadFlowParametersInfos(parametersRepository.findById(parametersUuid).get());

        assertThat(updatedParameters).recursivelyEquals(parametersToUpdate);
    }

    @Test
    void testResetToDefaultValues() throws Exception {
        limitReductionService.setDefaultValues(List.of(List.of(1.0, 1.0, 1.0, 1.0), List.of(1.0, 1.0, 1.0, 1.0)));

        LoadFlowParametersInfos defaultValues = parametersService.getDefaultParametersValues(defaultLoadFlowProvider);
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        LoadFlowParametersInfos parametersToUpdate = LoadFlowParametersInfos.builder()
                .provider(defaultLoadFlowProvider)
                .commonParameters(loadFlowParameters)
                .specificParametersPerProvider(Map.of())
                .build();
        UUID parametersUuid = saveAndReturnId(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        LoadFlowParametersInfos updatedParameters = parametersService.toLoadFlowParametersInfos(parametersRepository.findById(parametersUuid).get());
        assertThat(updatedParameters).recursivelyEquals(defaultValues);
    }

    @Test
    void testDelete() throws Exception {

        LoadFlowParametersInfos parametersToDelete = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();

        List<LoadFlowParametersEntity> storedParameters = parametersRepository.findAll();

        assertThat(storedParameters).isEmpty();
    }

    @Test
    void testDuplicate() throws Exception {
        LoadFlowParametersInfos parametersToDuplicate = buildParameters();

        UUID parametersUuid = saveAndReturnId(parametersToDuplicate);

        mockMvc.perform(post(URI_PARAMETERS_BASE).queryParam("duplicateFrom", parametersUuid.toString()))
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
        LoadFlowParametersInfos parameters = buildParameters();

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
    void testGetAll() throws Exception {
        LoadFlowParametersInfos parameters1 = buildParameters();

        LoadFlowParametersInfos parameters2 = buildParametersUpdate();

        saveAndReturnId(parameters1);

        saveAndReturnId(parameters2);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_BASE))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<LoadFlowParametersInfos> receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedParameters).hasSize(2);
    }

    @Test
    void testUpdateProvider() throws Exception {
        LoadFlowParametersInfos parameters = buildParameters();

        UUID parametersUuid = saveAndReturnId(parameters);

        String newProvider = "newProvider";

        mockMvc.perform(patch(URI_PARAMETERS_BASE + "/" + parametersUuid + "/provider")
                .content(newProvider))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos updatedParameters = parametersService.toLoadFlowParametersInfos(parametersRepository.findById(parametersUuid).get());

        assertThat(updatedParameters.getProvider()).isEqualTo(newProvider);
    }

    @Test
    void testResetProvider() throws Exception {
        LoadFlowParametersInfos parameters = buildParameters();

        UUID parametersUuid = saveAndReturnId(parameters);

        mockMvc.perform(patch(URI_PARAMETERS_BASE + "/" + parametersUuid + "/provider"))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersEntity securityAnalysisParametersEntity = parametersRepository.findById(parametersUuid).orElseThrow();

        assertEquals(defaultLoadFlowProvider, securityAnalysisParametersEntity.getProvider());
    }

    @Test
    void testGetParametersValues() {
        LoadFlowParametersInfos parameters = buildParameters();

        UUID parametersUuid = saveAndReturnId(parameters);

        LoadFlowParametersValues parametersValues = parametersService.getParametersValues(parametersUuid);

        assertThat(parametersService.toLoadFlowParametersValues(parametersRepository.findById(parametersUuid).get())).recursivelyEquals(parametersValues);
    }

    /** Save parameters into the repository and return its UUID. */
    protected UUID saveAndReturnId(LoadFlowParametersInfos parametersInfos) {
        parametersRepository.save(parametersInfos.toEntity());
        return parametersRepository.findAll().get(0).getId();
    }

    protected LoadFlowParametersInfos.LoadFlowParametersInfosBuilder buildCommonAndSpecificParameters() {
        return LoadFlowParametersInfos.builder()
                .commonParameters(LoadFlowParameters.load()).specificParametersPerProvider(Map.of());
    }

    protected LoadFlowParametersInfos buildParametersByProvider(String provider) {
        return buildCommonAndSpecificParameters()
                .provider(provider)
                .build();
    }

    protected LoadFlowParametersInfos buildParameters() {
        return buildParametersByProvider(PROVIDER);
    }

    protected LoadFlowParametersInfos buildParametersUpdate() {
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setDc(true);
        return LoadFlowParametersInfos.builder()
            .provider(PROVIDER)
            .commonParameters(loadFlowParameters)
            .specificParametersPerProvider(Map.of())
             .build();
    }
}
