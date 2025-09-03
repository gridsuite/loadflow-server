/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto.modifications;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Data
public class LoadFlowModificationInfos {

    public record TwoWindingsTransformerModification(String twoWindingsTransformerId, Integer initialTapPosition,
                                                     Integer solvedTapPosition, TapPositionType type) {
    }


    public record ShuntCompensatorModification(String shuntCompensatorId, Integer initialSectionCount, Integer solvedSectionCount) {
    }

    private List<TwoWindingsTransformerModification> twoWindingsTransformerModifications = new ArrayList<>();
    private List<ShuntCompensatorModification> shuntCompensatorModifications = new ArrayList<>();

    public void add2WTTapPositionValues(String twoWindingsTransformerId, Integer initialTapPosition, Integer solvedTapPosition, TapPositionType type) {
        twoWindingsTransformerModifications.add(new TwoWindingsTransformerModification(twoWindingsTransformerId, initialTapPosition, solvedTapPosition, type));
    }

    public void addSCSectionCountValue(String shuntCompensatorId, Integer initialSectionCount, Integer solvedSectionCount) {
        shuntCompensatorModifications.add(new ShuntCompensatorModification(shuntCompensatorId, initialSectionCount, solvedSectionCount));
    }
}
