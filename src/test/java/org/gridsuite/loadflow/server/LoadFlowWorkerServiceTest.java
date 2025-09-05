/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.util.LimitViolationUtils;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.security.LimitViolationType;
import io.micrometer.observation.Observation;
import org.gridsuite.computation.service.AbstractResultContext;
import org.gridsuite.computation.service.ExecutionService;
import org.gridsuite.computation.service.NotificationService;
import org.gridsuite.computation.service.ReportService;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.parameters.LoadFlowParametersValues;
import org.gridsuite.loadflow.server.service.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.le-saulnier at rte-france.com>
 */
@SpringBootTest
class LoadFlowWorkerServiceTest {
    @Test
    void testGetNextLimitName() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits(new NetworkFactoryImpl());
        LimitViolationInfos limitViolationInfos = LimitViolationInfos.builder()
            .subjectId("NHV1_NHV2_1")
            .locationId("VLHV1")
            .limit(1200D)
            .limitName("10'")
            .actualOverloadDuration(600)
            .upComingOverloadDuration(60)
            .value(1300D)
            .side("TWO")
            .limitType(LimitViolationType.CURRENT)
            .build();
        String nextLimitName = LoadFlowWorkerService.getNextLimitName(limitViolationInfos, network);

        // nextLimitName should be the one after the "limitName" set in limitViolationInfos
        String expectedNextLimitName = "1'";
        Assertions.assertEquals(expectedNextLimitName, nextLimitName);
    }

    @Test
    void testGetPatlNextLimitName() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits(new NetworkFactoryImpl());
        LimitViolationInfos limitViolationInfos = LimitViolationInfos.builder()
            .subjectId("NHV1_NHV2_1")
            .locationId("VLHV1")
            .limit(1100D)
            .limitName(LimitViolationUtils.PERMANENT_LIMIT_NAME)
            .upComingOverloadDuration(600)
            .value(1150D)
            .side("TWO")
            .limitType(LimitViolationType.CURRENT)
            .build();
        String nextLimitName = LoadFlowWorkerService.getNextLimitName(limitViolationInfos, network);

        // nextLimitName should be the first one of the list, since PERMANENT_LIMIT_NAME is set in limitViolationInfos
        String expectedNextLimitName = "10'";
        Assertions.assertEquals(expectedNextLimitName, nextLimitName);
    }

    @Test
    void testGetNoTemporaryLimitNextLimitName() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits(new NetworkFactoryImpl());

        LimitViolationInfos limitViolationInfos = LimitViolationInfos.builder()
            .subjectId("NHV1_NHV2_1")
            .locationId("VLHV1")
            .limit(500D)
            .limitName(LimitViolationUtils.PERMANENT_LIMIT_NAME)
            .value(600D)
            .side("ONE")
            .limitType(LimitViolationType.CURRENT)
            .build();

        String nextLimitName = LoadFlowWorkerService.getNextLimitName(limitViolationInfos, network);

        // nextLimitName should be null, since PERMANENT_LIMIT_NAME is set in limitViolationInfos and no temporary limit is set
        String expectedNextLimitName = null;
        Assertions.assertEquals(expectedNextLimitName, nextLimitName);
    }

    @Test
    void testFlushIsCalledBeforeInsertResults() {
        LoadFlowObserver observer = mock(LoadFlowObserver.class);
        LoadFlowResultService resultService = mock(LoadFlowResultService.class);
        LoadFlowWorkerService service = new LoadFlowWorkerService(
                mock(NetworkStoreService.class), mock(NotificationService.class), mock(ReportService.class),
                resultService, mock(ExecutionService.class), observer, mock(ObjectMapper.class),
                mock(LimitReductionService.class));

        Network network = mock(Network.class);
        LoadFlowRunContext runContext = mock(LoadFlowRunContext.class);
        AbstractResultContext<LoadFlowRunContext> resultContext = mock(AbstractResultContext.class);
        com.powsybl.loadflow.LoadFlowResult result = mock(com.powsybl.loadflow.LoadFlowResult.class);
        LoadFlowParametersValues parametersValues = mock(LoadFlowParametersValues.class);
        when(resultContext.getRunContext()).thenReturn(runContext);
        when(resultContext.getResultUuid()).thenReturn(UUID.randomUUID());
        when(runContext.isApplySolvedValues()).thenReturn(false);
        when(runContext.getNetwork()).thenReturn(network);
        when(runContext.buildParameters()).thenReturn(mock(LoadFlowParameters.class));
        when(runContext.getParameters()).thenReturn(parametersValues);
        when(parametersValues.getLimitReduction()).thenReturn(0.8f);
        when(result.isFailed()).thenReturn(false);

        service.saveResult(network, resultContext, result);

        // Verify results save (flush) is done before inserting results in DB
        InOrder inOrder = inOrder(observer, resultService);
        inOrder.verify(observer).observe(eq("network.save"), eq(runContext), any(Observation.CheckedRunnable.class));
        inOrder.verify(resultService).insert(any(UUID.class), eq(result), any(), any(), any());
    }
}
