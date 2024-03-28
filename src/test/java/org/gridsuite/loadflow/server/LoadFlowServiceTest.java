package org.gridsuite.loadflow.server;

import com.powsybl.security.LimitViolationType;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.repositories.LimitViolationRepository;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class LoadFlowServiceTest {

    private static final UUID RESULT_UUID = UUID.randomUUID();
    @MockBean
    private LimitViolationRepository limitViolationRepository;
    @Autowired
    private LoadFlowService loadFlowService;

    @Test
    void assertResultExistsWhenResultDoesNotExistShouldReturnEmptyResult() {
        UUID nonExistingUuid = UUID.randomUUID();
        when(limitViolationRepository.existsLimitViolationEntitiesByLoadFlowResultResultUuid(nonExistingUuid)).thenReturn(false);
        List<LimitViolationInfos> result = loadFlowService.getLimitViolationsInfos(nonExistingUuid, null, null, null, null, null);
        assertEquals(0, result.size());
    }

    @Test
    void getLimitViolationsInfosWhenCalledReturnsCorrectData() {

        String stringFilters = "[{\"column\":\"subjectId\",\"dataType\":\"text\",\"type\":\"startsWith\",\"value\":\"Sub\"}]";
        Sort sort = Sort.unsorted();

        when(limitViolationRepository.existsLimitViolationEntitiesByLoadFlowResultResultUuid(RESULT_UUID)).thenReturn(true);
        when(limitViolationRepository.findAll(any(Specification.class), eq(sort)))
                .thenReturn(LimitViolationsMock.limitViolationEntities);
        List<LimitViolationInfos> result = loadFlowService.getLimitViolationsInfos(RESULT_UUID, stringFilters, null, sort, null, null);

        assertNotNull(result);
        assertEquals(LimitViolationsMock.limitViolationEntities.size(), result.size());
        verify(limitViolationRepository, times(1)).findAll(any(Specification.class), eq(sort));
    }

    private static final class LimitViolationsMock {
        static List<LimitViolationEntity> limitViolationEntities = Arrays.asList(
                LimitViolationEntity.builder()
                        .id(UUID.randomUUID())
                        .loadFlowResult(LoadFlowResultEntity.builder().resultUuid(RESULT_UUID).build())
                        .subjectId("Subject1")
                        .limit(100.0)
                        .limitName("Voltage")
                        .actualOverload(110)
                        .upComingOverload(120)
                        .overload(10.0)
                        .value(110.0)
                        .side("A")
                        .limitType(LimitViolationType.CURRENT)
                        .build(),
                LimitViolationEntity.builder()
                        .id(UUID.randomUUID())
                        .loadFlowResult(LoadFlowResultEntity.builder().resultUuid(RESULT_UUID).build())
                        .subjectId("Subject2")
                        .limit(200.0)
                        .limitName("Current")
                        .actualOverload(210)
                        .upComingOverload(220)
                        .overload(20.0)
                        .value(210.0)
                        .side("B")
                        .limitType(LimitViolationType.CURRENT)
                        .build()
        );
    }
}
