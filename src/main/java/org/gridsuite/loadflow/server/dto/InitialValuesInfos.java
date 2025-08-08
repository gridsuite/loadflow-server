/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Data
public class InitialValuesInfos {

    public record TwoWindingsTransformerValues(String twoWindingsTransformerId, Integer ratioTapPosition,
                                               Integer phaseTapPosition) {
    }


    public record ShuntCompensatorValues(String shuntCompensatorId, Integer sectionCount) {
    }

    private List<TwoWindingsTransformerValues> twoWindingsTransformerValues = new ArrayList<>();
    private List<ShuntCompensatorValues> shuntCompensatorValues = new ArrayList<>();

    public void add2WTTapPositionValues(String twoWindingsTransformerId, Integer ratioTapPosition, Integer phaseTapPosition) {
        twoWindingsTransformerValues.add(new TwoWindingsTransformerValues(twoWindingsTransformerId, ratioTapPosition, phaseTapPosition));
    }

    public void addSCSectionCountValue(String shuntCompensatorId, Integer sectionCount) {
        shuntCompensatorValues.add(new ShuntCompensatorValues(shuntCompensatorId, sectionCount));
    }
}
