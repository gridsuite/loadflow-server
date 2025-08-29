/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server;

import com.powsybl.iidm.network.Network;
import org.gridsuite.loadflow.server.dto.InitialValuesInfos;
import org.gridsuite.loadflow.server.dto.modifications.*;
import org.gridsuite.loadflow.utils.assertions.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@SpringBootTest
class LoadFlowModificationMapperTest {

    @Test
    void testMapper() {
        Network network = Mockito.mock(Network.class, RETURNS_DEEP_STUBS);

        InitialValuesInfos initialValuesMock = new InitialValuesInfos();
        initialValuesMock.add2WTTapPositionValues("test2wtId", 10, null);
        initialValuesMock.add2WTTapPositionValues("test2wt2Id", null, 15);
        initialValuesMock.addSCSectionCountValue("shuntCompensatorId", 20);

        LoadFlowModificationInfos expectedResult = new LoadFlowModificationInfos(
            List.of(
                new LoadFlowTwoWindingsTransformerModificationInfos("test2wtId", 10, 12, TapPositionType.RATIO_TAP),
                new LoadFlowTwoWindingsTransformerModificationInfos("test2wt2Id", 15, 17, TapPositionType.PHASE_TAP)
            ),
            List.of(
                new LoadFlowShuntCompensatorModificationInfos("shuntCompensatorId", 20, 22)
            )
        );

        Mockito.when(network.getTwoWindingsTransformer("test2wtId").getRatioTapChanger().getTapPosition()).thenReturn(12);
        Mockito.when(network.getTwoWindingsTransformer("test2wt2Id").getPhaseTapChanger().getTapPosition()).thenReturn(17);
        Mockito.when(network.getShuntCompensator("shuntCompensatorId").getSectionCount()).thenReturn(22);

        Assertions.assertThat(expectedResult).usingRecursiveComparison().isEqualTo(LoadFlowModificationMapper.mapInitialValuesToLoadFlowModifications(initialValuesMock, network));
    }
}
