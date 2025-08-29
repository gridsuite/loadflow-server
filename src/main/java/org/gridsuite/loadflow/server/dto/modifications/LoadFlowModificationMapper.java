/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto.modifications;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import org.gridsuite.loadflow.server.dto.InitialValuesInfos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
public final class LoadFlowModificationMapper {
    private LoadFlowModificationMapper() throws IllegalAccessException {
        throw new IllegalAccessException("Utility class can not be initialize.");
    }

    public static LoadFlowModificationInfos mapInitialValuesToLoadFlowModifications(InitialValuesInfos initialValuesInfos, Network network) {
        List<LoadFlowTwoWindingsTransformerModificationInfos> twoWindingsTransformerModificationInfos = initialValuesInfos.getTwoWindingsTransformerValues().stream()
            .flatMap(t -> toTwoWindingsTransformerModification(t, network).stream())
            .filter(Objects::nonNull)
            .toList();

        List<LoadFlowShuntCompensatorModificationInfos> shuntCompensatorModificationInfos = initialValuesInfos.getShuntCompensatorValues().stream()
            .map(s -> toShuntCompensatorModification(s, network))
            .filter(Objects::nonNull)
            .toList();

        return new LoadFlowModificationInfos(twoWindingsTransformerModificationInfos, shuntCompensatorModificationInfos);
    }

    private static List<LoadFlowTwoWindingsTransformerModificationInfos> toTwoWindingsTransformerModification(InitialValuesInfos.TwoWindingsTransformerValues twoWindingsTransformerValues, Network network) {
        List<LoadFlowTwoWindingsTransformerModificationInfos> result = new ArrayList<>();

        TwoWindingsTransformer twoWindingsTransformer = network.getTwoWindingsTransformer(twoWindingsTransformerValues.twoWindingsTransformerId());
        if (twoWindingsTransformer == null) {
            return result;
        }

        if (twoWindingsTransformerValues.ratioTapPosition() != null) {
            result.add(new LoadFlowTwoWindingsTransformerModificationInfos(
                twoWindingsTransformerValues.twoWindingsTransformerId(),
                twoWindingsTransformerValues.ratioTapPosition(),
                twoWindingsTransformer.getRatioTapChanger().getTapPosition(),
                TapPositionType.RATIO_TAP
            ));
        }

        if (twoWindingsTransformerValues.phaseTapPosition() != null) {
            result.add(new LoadFlowTwoWindingsTransformerModificationInfos(
                twoWindingsTransformerValues.twoWindingsTransformerId(),
                twoWindingsTransformerValues.phaseTapPosition(),
                twoWindingsTransformer.getPhaseTapChanger().getTapPosition(),
                TapPositionType.PHASE_TAP
            ));
        }

        return result;
    }

    private static LoadFlowShuntCompensatorModificationInfos toShuntCompensatorModification(InitialValuesInfos.ShuntCompensatorValues shuntCompensatorValues, Network network) {
        ShuntCompensator shuntCompensator = network.getShuntCompensator(shuntCompensatorValues.shuntCompensatorId());
        if (shuntCompensator == null) {
            return null;
        }

        return new LoadFlowShuntCompensatorModificationInfos(
            shuntCompensatorValues.shuntCompensatorId(),
            shuntCompensatorValues.sectionCount(),
            shuntCompensator.getSectionCount()
        );
    }
}
