/**
 Copyright (c) 2024, RTE (http://www.rte-france.com)
 This Source Code Form is subject to the terms of the Mozilla Public
 License, v. 2.0. If a copy of the MPL was not distributed with this
 file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto.parameters;

import lombok.*;

import java.util.List;

/**
 * @author Maissa SOUISSI <maissa.souissi at rte-france.com>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitReductionsByVoltageLevel {

    @Setter
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class LimitDuration {
        private Integer lowBound;
        private boolean lowClosed;
        private Integer highBound;
        private boolean highClosed;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class VoltageLevel {
        private double nominalV;
        private double lowBound;
        private double highBound;
    }

    @Builder
    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class LimitReduction {
        double reduction;
        LimitDuration limitDuration;
    }

    private VoltageLevel voltageLevel;
    private double permanentLimitReduction;
    private List<LimitReduction> temporaryLimitReductions;
}

