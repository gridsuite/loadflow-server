/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.gridsuite.loadflow.utils.assertions.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersInfos;
import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.gridsuite.loadflow.server.repositories.parameters.LoadFlowParametersRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowParameters;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class LoadFlowParametersTest {

    private static final String URI_PARAMETERS_BASE = "/v1/parameters";

    private static final String URI_PARAMETERS_GET_PUT = URI_PARAMETERS_BASE + "/";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    private LoadFlowParametersRepository parametersRepository;

    @Before
    public void setup() {
        parametersRepository.deleteAll();
    }

    @After
    public void tearOff() {
        parametersRepository.deleteAll();
    }

    @Test
    public void testCreate() throws Exception {

        LoadFlowParametersInfos parametersToCreate = buildParameters();
        String parametersToCreateJson = mapper.writeValueAsString(parametersToCreate);

        mockMvc.perform(post(URI_PARAMETERS_BASE).content(parametersToCreateJson).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos createdParameters = parametersRepository.findAll().get(0).toLoadFlowParametersInfos();

        assertThat(createdParameters).recursivelyEquals(parametersToCreate);
    }

    @Test
    public void testCreateWithDefaultValues() throws Exception {
        LoadFlowParametersInfos defaultParameters = LoadFlowParametersInfos.builder()
            .commonParameters(LoadFlowParameters.load())
            .specificParametersPerProvider(Map.of())
            .build();

        mockMvc.perform(post(URI_PARAMETERS_BASE + "/default"))
                .andExpect(status().isOk()).andReturn();

        LoadFlowParametersInfos createdParameters = parametersRepository.findAll().get(0).toLoadFlowParametersInfos();

        assertThat(createdParameters).recursivelyEquals(defaultParameters);
    }

    @Test
    public void testRead() throws Exception {

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
    public void testUpdate() throws Exception {

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
    public void testDelete() throws Exception {

        LoadFlowParametersInfos parametersToDelete = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToDelete);

        mockMvc.perform(delete(URI_PARAMETERS_GET_PUT + parametersUuid)).andExpect(status().isOk()).andReturn();

        List<LoadFlowParametersEntity> storedParameters = parametersRepository.findAll();

        assertTrue(storedParameters.isEmpty());
    }

    @Test
    public void testDuplicate() throws Exception {
        LoadFlowParametersInfos parametersToDuplicate = buildParameters();

        UUID parametersUuid = saveAndRetunId(parametersToDuplicate);

        mockMvc.perform(post(URI_PARAMETERS_BASE + "?duplicateFrom=" + parametersUuid))
                .andExpect(status().isOk()).andReturn();

        List<LoadFlowParametersEntity> storedParameters = parametersRepository.findAll();

        assertThat(storedParameters).hasSize(2);
    }

    @Test
    public void testGetWithInvalidId() throws Exception {
        mockMvc.perform(get(URI_PARAMETERS_GET_PUT + UUID.randomUUID()))
                .andExpect(status().isNotFound()).andReturn();
    }

    @Test
    public void testGetAll() throws Exception {
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

    /** Save parameters into the repository and return its UUID. */
    protected UUID saveAndRetunId(LoadFlowParametersInfos parametersInfos) {
        parametersRepository.save(parametersInfos.toEntity());
        return parametersRepository.findAll().get(0).getId();
    }

    protected LoadFlowParametersInfos buildParameters() {
        return LoadFlowParametersInfos.builder()
            .commonParameters(LoadFlowParameters.load())
            .specificParametersPerProvider(Map.of())
            .build();
    }

    protected LoadFlowParametersInfos buildParametersUpdate() {
        LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
        loadFlowParameters.setDc(true);
        return LoadFlowParametersInfos.builder()
            .commonParameters(loadFlowParameters)
            .specificParametersPerProvider(Map.of())
            .build();
    }
}
