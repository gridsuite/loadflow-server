package org.gridsuite.loadflow.server.dto.modifications;

public record LoadFlowTwoWindingsTransformerModificationInfos(String twoWindingsTransformerId, Integer tapPositionIn, Integer tapPositionOut, TapPositionType type) { }
