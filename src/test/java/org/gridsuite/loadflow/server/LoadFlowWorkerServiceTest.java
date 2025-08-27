package org.gridsuite.loadflow.server;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.util.LimitViolationUtils;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.security.LimitViolationType;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.service.LoadFlowWorkerService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
}
