/**
 Copyright (c) 2024, RTE (http://www.rte-france.com)
 This Source Code Form is subject to the terms of the Mozilla Public
 License, v. 2.0. If a copy of the MPL was not distributed with this
 file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.service;

import com.powsybl.ws.commons.computation.ComputationException;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.Range;
import org.gridsuite.loadflow.server.dto.parameters.LimitReductionsByVoltageLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Maissa SOUISSI <maissa.souissi at rte-france.com>
 */
@Setter
@Getter
@Service
@ConfigurationProperties(prefix = "loadflow.default-limit-reductions")
public class LimitReductionService {
    private Set<String> providers;
    private List<LimitReductionsByVoltageLevel.VoltageLevel> voltageLevels;
    private List<LimitReductionsByVoltageLevel.LimitDuration> limitDurations;
    private List<List<Double>> defaultValues;

    public List<LimitReductionsByVoltageLevel> createDefaultLimitReductions() {
        return createLimitReductions(defaultValues);
    }

    public List<LimitReductionsByVoltageLevel> createLimitReductions(List<List<Double>> values) {
        assertValidConfig(values);
        List<LimitReductionsByVoltageLevel> limitReductions = new ArrayList<>(voltageLevels.size());
        AtomicInteger index = new AtomicInteger(0);
        voltageLevels.forEach(vl -> {
            LimitReductionsByVoltageLevel.LimitReductionsByVoltageLevelBuilder builder = LimitReductionsByVoltageLevel.builder().voltageLevel(vl);
            List<Double> valuesByVl = values.get(index.getAndIncrement());
            builder.permanentLimitReduction(valuesByVl.get(0));
            builder.temporaryLimitReductions(getLimitReductionsByDuration(valuesByVl));
            limitReductions.add(builder.build());
        });

        return limitReductions;
    }

    private List<LimitReductionsByVoltageLevel.LimitReduction> getLimitReductionsByDuration(List<Double> values) {
        List<LimitReductionsByVoltageLevel.LimitReduction> limitReductions = new ArrayList<>(limitDurations.size());
        AtomicInteger index = new AtomicInteger(1);
        limitDurations.forEach(limitDuration ->
                limitReductions.add(
                        LimitReductionsByVoltageLevel.LimitReduction.builder()
                                .limitDuration(limitDuration)
                                .reduction(values.get(index.getAndIncrement()))
                                .build()
                )
        );
        return limitReductions;
    }

    private void assertValidConfig(List<List<Double>> values) {
        if (voltageLevels.isEmpty()) {
            throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "No configuration for voltage levels");
        }

        if (limitDurations.isEmpty()) {
            throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "No configuration for limit durations");
        }

        if (values.isEmpty() || values.get(0).isEmpty()) {
            throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "No values provided");
        }

        int nbValuesByVl = values.get(0).size();
        if (values.stream().anyMatch(valuesByVl -> valuesByVl.size() != nbValuesByVl)) {
            throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "Number of values for a voltage level is incorrect");
        }

        if (voltageLevels.size() < values.size()) {
            throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "Too many values provided for voltage levels");
        }

        if (voltageLevels.size() > values.size()) {
            throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "Not enough values provided for voltage levels");
        }

        if (limitDurations.size() < nbValuesByVl - 1) {
            throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "Too many values provided for limit durations");
        }

        if (limitDurations.size() > nbValuesByVl - 1) {
            throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "Not enough values provided for limit durations");
        }

        values.forEach(valuesByVl -> {
            if (valuesByVl.stream().anyMatch(v -> !Range.of(0.0, 1.0).contains(v))) {
                throw new ComputationException(ComputationException.Type.LIMIT_REDUCTION_CONFIG_ERROR, "Value not between 0 and 1");
            }
        });
    }
}
