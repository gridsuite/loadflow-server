package org.gridsuite.loadflow.server;

import com.powsybl.iidm.network.Network;
import com.powsybl.security.*;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.gridsuite.loadflow.server.repositories.LimitViolationRepository;
import org.gridsuite.loadflow.server.service.LoadFlowService;
import org.gridsuite.loadflow.server.utils.LoadflowResultsUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.loadflow.server.Networks.createNetwork;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class LoadFlowServiceTest {

    @MockBean
    private LimitViolationRepository limitViolationRepository;

    @Autowired
    private LoadFlowService loadFlowService;

    private static final UUID RESULT_UUID = UUID.randomUUID();

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

    @Test
    void testFormatNodeIdSingleNode() {
        String result = LoadflowResultsUtils.formatNodeId(List.of("Node1"), "Subject1");
        assertEquals("Node1", result);
    }

    @Test
    void testFormatNodeIdEmptyNodes() {
        String result = LoadflowResultsUtils.formatNodeId(List.of(), "Subject1");
        assertEquals("Subject1", result);
    }

    @Test
    void testFormatNodeIdMultipleNodes() {
        String result = LoadflowResultsUtils.formatNodeId(List.of("Node1", "Node2"), "Subject1");
        assertEquals("Subject1 (Node1, Node2 )", result);
    }

    @Test
    void testGetIdFromViolationWithBusBreaker() {
        // Setup
        Network network = createNetwork("", true);
        BusBreakerViolationLocation busBreakerLocation = new BusBreakerViolationLocation(List.of("NGEN"));
        LimitViolation limitViolation = mock(LimitViolation.class);
        when(limitViolation.getViolationLocation()).thenReturn(Optional.of(busBreakerLocation));

        assertEquals("NGEN", LoadflowResultsUtils.getIdFromViolation(limitViolation, network));
    }

    @Test
    void testGetIdFromViolationWithNoViolationLocation() {
        // Setup
        Network network = mock(Network.class);
        LimitViolation limitViolation = mock(LimitViolation.class);

        when(limitViolation.getViolationLocation()).thenReturn(Optional.empty());
        when(limitViolation.getSubjectId()).thenReturn("SubjectId");

        assertEquals("SubjectId", LoadflowResultsUtils.getIdFromViolation(limitViolation, network));
    }
}
