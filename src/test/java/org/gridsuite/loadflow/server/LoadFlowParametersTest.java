/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

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
    String defaultLoadflowProvider;

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

        LoadFlowParametersInfos createdParameters = parametersRepository.findAll().get(0).toLoadFlowParametersInfos();

        assertThat(createdParameters).recursivelyEquals(parametersToCreate);
    }

    @Test
    void testCreateWithDefaultValues() throws Exception {
        LoadFlowParametersInfos defaultLoadFlowParameters = parametersService.getDefaultParametersValues(defaultLoadflowProvider, limitReductionService);
        mockMvc.perform(post(URI_PARAMETERS_BASE + "/default"))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos createdParameters = parametersRepository.findAll().get(0).toLoadFlowParametersInfos();

        assertThat(createdParameters).recursivelyEquals(defaultLoadFlowParameters);
    }

    @Test
    void testRead() throws Exception {

        LoadFlowParametersInfos parametersToRead = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToRead);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        LoadFlowParametersInfos receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        assertThat(receivedParameters).recursivelyEquals(parametersToRead);
    }

    @Test
    void testUpdate() throws Exception {

        LoadFlowParametersInfos parametersToUpdate = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToUpdate);

        parametersToUpdate = buildParametersUpdate();

        String parametersToUpdateJson = mapper.writeValueAsString(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).content(parametersToUpdateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        LoadFlowParametersInfos updatedParameters = parametersRepository.findById(parametersUuid).get().toLoadFlowParametersInfos();

        assertThat(updatedParameters).recursivelyEquals(parametersToUpdate);
    }

    @Test
    void testResetToDefaultValues() throws Exception {
        LoadFlowParametersInfos defaultValues = buildParameters();
        LoadFlowParametersInfos parametersToUpdate = buildParametersUpdate();

        UUID parametersUuid = saveAndRetunId(parametersToUpdate);

        mockMvc.perform(put(URI_PARAMETERS_GET_PUT + parametersUuid).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        LoadFlowParametersInfos updatedParameters = parametersRepository.findById(parametersUuid).get().toLoadFlowParametersInfos();

        assertThat(updatedParameters).recursivelyEquals(defaultValues);
    }

    @Test
    void testDelete() throws Exception {

        LoadFlowParametersInfos parametersToDelete = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();

        List<LoadFlowParametersEntity> storedParameters = parametersRepository.findAll();

        assertThat(storedParameters).isEmpty();
    }

    @Test
    void testDuplicate() throws Exception {
        LoadFlowParametersInfos parametersToDuplicate = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToDuplicate);

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

        UUID parametersUuid = saveAndRetunId(parameters);

        MvcResult mvcResult = mockMvc.perform(get(URI_PARAMETERS_GET_PUT + parametersUuid + "/values" + "?provider=" + PROVIDER))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        LoadFlowParametersValues receivedParameters = mapper.readValue(resultAsString, new TypeReference<>() {
        });

        LoadFlowParametersValues parametersValues = parameters.toEntity().toLoadFlowParametersValues(PROVIDER);

        assertThat(receivedParameters).recursivelyEquals(parametersValues);
    }

    @Test
    void testGetAll() throws Exception {
        LoadFlowParametersInfos parameters1 = buildParameters();

        LoadFlowParametersInfos parameters2 = buildParametersUpdate();

        saveAndRetunId(parameters1);

        saveAndRetunId(parameters2);

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

        UUID parametersUuid = saveAndRetunId(parameters);

        String newProvider = "newProvider";

        mockMvc.perform(patch(URI_PARAMETERS_BASE + "/" + parametersUuid + "/provider")
                .content(newProvider))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos updatedParameters = parametersRepository.findById(parametersUuid).get().toLoadFlowParametersInfos();

        assertThat(updatedParameters.getProvider()).isEqualTo(newProvider);
    }

    @Test
    void testResetProvider() throws Exception {
        LoadFlowParametersInfos parameters = buildParameters();

        UUID parametersUuid = saveAndRetunId(parameters);

        mockMvc.perform(patch(URI_PARAMETERS_BASE + "/" + parametersUuid + "/provider"))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos updatedParameters = parametersRepository.findById(parametersUuid).get().toLoadFlowParametersInfos();

        assertThat(updatedParameters.getProvider()).isEqualTo(defaultLoadflowProvider);
    }

    @Test
    void testGetParametersValues() {
        LoadFlowParametersInfos parameters = buildParameters();

        UUID parametersUuid = saveAndRetunId(parameters);

        LoadFlowParametersValues parametersValues = parametersService.getParametersValues(parametersUuid);

        assertThat(parametersRepository.findById(parametersUuid).get().toLoadFlowParametersValues()).recursivelyEquals(parametersValues);
    }

    /** Save parameters into the repository and return its UUID. */
    protected UUID saveAndRetunId(LoadFlowParametersInfos parametersInfos) {
        parametersRepository.save(parametersInfos.toEntity());
        return parametersRepository.findAll().get(0).getId();
    }

    protected LoadFlowParametersInfos buildParameters() {
        return LoadFlowParametersInfos.builder()
            .provider(PROVIDER)
            .commonParameters(LoadFlowParameters.load())
            .specificParametersPerProvider(Map.of())
            .build();
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
