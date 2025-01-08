/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowProvider;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.service.LoadFlowRunContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoadFlowRunContextTest {

    @Test
    void testBuildParametersWithNullParameters() {
        LoadFlowRunContext context = LoadFlowRunContext.builder()
                .parameters(null)
                .build();

        LoadFlowParameters params = context.buildParameters();
        assertNotNull(params);
    }

    @Test
    void testBuildParametersWithCommonParameters() {
        LoadFlowParameters commonParams = LoadFlowParameters.load();
        LoadFlowParametersValues parametersValues = LoadFlowParametersValues.builder()
                .commonParameters(commonParams)
                .build();

        LoadFlowRunContext context = LoadFlowRunContext.builder()
                .parameters(parametersValues)
                .build();

        LoadFlowParameters params = context.buildParameters();
        assertEquals(commonParams, params);
    }

    @Test
    void testBuildParametersWithSpecificParameters() {
        LoadFlowParameters commonParams = LoadFlowParameters.load();
        Map<String, String> specificParams = Map.of("key", "value");
        LoadFlowParametersValues parametersValues = LoadFlowParametersValues.builder()
                .commonParameters(commonParams)
                .specificParameters(specificParams)
                .build();

        LoadFlowProvider provider = mock(LoadFlowProvider.class);
        Extension<LoadFlowParameters> extension = mock(Extension.class);

        try (MockedStatic<LoadFlowProvider> mockedProvider = Mockito.mockStatic(LoadFlowProvider.class)) {
            mockedProvider.when(LoadFlowProvider::findAll).thenReturn(Collections.singletonList(provider));
            when(provider.getName()).thenReturn("provider");
            when(provider.loadSpecificParameters(any(PlatformConfig.class))).thenReturn(Optional.of(extension));

            LoadFlowRunContext context = LoadFlowRunContext.builder()
                    .provider("provider")
                    .parameters(parametersValues)
                    .build();

            LoadFlowParameters params = context.buildParameters();
            assertNotNull(params);
            verify(provider).updateSpecificParameters(extension, specificParams);
        }
    }

    @Test
    void testBuildParametersWithProviderNotFound() {
        LoadFlowParameters commonParams = LoadFlowParameters.load();
        Map<String, String> specificParams = Map.of("key", "value");
        LoadFlowParametersValues parametersValues = LoadFlowParametersValues.builder()
                .commonParameters(commonParams)
                .specificParameters(specificParams)
                .build();

        try (MockedStatic<LoadFlowProvider> mockedProvider = Mockito.mockStatic(LoadFlowProvider.class)) {
            mockedProvider.when(LoadFlowProvider::findAll).thenReturn(Collections.emptyList());

            LoadFlowRunContext context = LoadFlowRunContext.builder()
                    .provider("provider")
                    .parameters(parametersValues)
                    .build();

            assertThrows(PowsyblException.class, context::buildParameters);
        }
    }

    @Test
    void testBuildParametersWithExtensionNotFound() {
        LoadFlowParameters commonParams = LoadFlowParameters.load();
        Map<String, String> specificParams = Map.of("key", "value");
        LoadFlowParametersValues parametersValues = LoadFlowParametersValues.builder()
                .commonParameters(commonParams)
                .specificParameters(specificParams)
                .build();

        LoadFlowProvider provider = mock(LoadFlowProvider.class);

        try (MockedStatic<LoadFlowProvider> mockedProvider = Mockito.mockStatic(LoadFlowProvider.class)) {
            mockedProvider.when(LoadFlowProvider::findAll).thenReturn(Collections.singletonList(provider));
            when(provider.getName()).thenReturn("provider");
            when(provider.loadSpecificParameters(any(PlatformConfig.class))).thenReturn(Optional.empty());

            LoadFlowRunContext context = LoadFlowRunContext.builder()
                    .provider("provider")
                    .parameters(parametersValues)
                    .build();

            assertThrows(PowsyblException.class, context::buildParameters);
        }
    }
}
