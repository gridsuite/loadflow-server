package org.gridsuite.loadflow.server.dto.modifications;

import java.util.List;

public record LoadFlowModificationInfos(List<LoadFlowTwoWindingsTransformerModificationInfos> twoWindingsTransformerModifications, List<LoadFlowShuntCompensatorModificationInfos> shuntCompensatorModifications) { }
