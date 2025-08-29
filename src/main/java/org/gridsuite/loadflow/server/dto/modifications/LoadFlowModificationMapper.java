package org.gridsuite.loadflow.server.dto.modifications;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import org.gridsuite.loadflow.server.dto.InitialValuesInfos;

import java.util.List;
import java.util.Objects;

public final class LoadFlowModificationMapper {
    private LoadFlowModificationMapper() throws IllegalAccessException {
        throw new IllegalAccessException("Utility class can not be initialize.");
    }

    public static LoadFlowModificationInfos mapInitialValuesToLoadFlowModifications(InitialValuesInfos initialValuesInfos, Network network) {
        List<LoadFlowTwoWindingsTransformerModificationInfos> twoWindingsTransformerModificationInfos = initialValuesInfos.getTwoWindingsTransformerValues().stream()
            .map(t -> toTwoWindingsTransformerModification(t, network))
            .filter(Objects::nonNull)
            .toList();

        List<LoadFlowShuntCompensatorModificationInfos> shuntCompensatorModificationInfos = initialValuesInfos.getShuntCompensatorValues().stream()
            .map(s -> toShuntCompensatorModification(s, network))
            .filter(Objects::nonNull)
            .toList();

        return new LoadFlowModificationInfos(twoWindingsTransformerModificationInfos, shuntCompensatorModificationInfos);
    }

    private static LoadFlowTwoWindingsTransformerModificationInfos toTwoWindingsTransformerModification(InitialValuesInfos.TwoWindingsTransformerValues twoWindingsTransformerValues, Network network) {
        TwoWindingsTransformer twoWindingsTransformer = network.getTwoWindingsTransformer(twoWindingsTransformerValues.twoWindingsTransformerId());
        if (twoWindingsTransformer == null) {
            return null;
        }
        TapPositionType tapPositionType = twoWindingsTransformerValues.phaseTapPosition() != null ? TapPositionType.PHASE_TAP : TapPositionType.RATIO_TAP;
        return new LoadFlowTwoWindingsTransformerModificationInfos(
            twoWindingsTransformerValues.twoWindingsTransformerId(),
            tapPositionType.equals(TapPositionType.PHASE_TAP)
                ? twoWindingsTransformerValues.phaseTapPosition()
                : twoWindingsTransformerValues.ratioTapPosition(),
            tapPositionType.equals(TapPositionType.PHASE_TAP)
                ? twoWindingsTransformer.getPhaseTapChanger().getTapPosition()
                : twoWindingsTransformer.getRatioTapChanger().getTapPosition(),
            tapPositionType
        );
    }

    private static LoadFlowShuntCompensatorModificationInfos toShuntCompensatorModification(InitialValuesInfos.ShuntCompensatorValues shuntCompensatorValues, Network network) {
        ShuntCompensator shuntCompensator = network.getShuntCompensator(shuntCompensatorValues.shuntCompensatorId());
        if (shuntCompensator == null) {
            return null;
        }

        return new LoadFlowShuntCompensatorModificationInfos(
            shuntCompensatorValues.shuntCompensatorId(),
            shuntCompensatorValues.sectionCount(),
            shuntCompensator.getSolvedSectionCount()
        );
    }
}
