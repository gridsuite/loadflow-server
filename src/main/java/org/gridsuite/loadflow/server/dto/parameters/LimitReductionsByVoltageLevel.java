package org.gridsuite.loadflow.server.dto.parameters;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitReductionsByVoltageLevel {

    @Setter
    @Getter
    @NoArgsConstructor
    public static class LimitDuration {
        private Integer lowBound;
        private boolean lowClosed;
        private Integer highBound;
        private boolean highClosed;
    }

    @Setter
    @Getter
    @NoArgsConstructor
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
    public static class LimitReduction {
        double reduction;
        LimitDuration limitDuration;
    }

    private VoltageLevel voltageLevel;
    private double permanentLimitReduction;
    List<LimitReduction> temporaryLimitReductions;
}

