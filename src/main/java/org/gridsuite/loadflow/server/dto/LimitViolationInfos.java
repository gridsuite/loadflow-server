/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.dto;

import com.powsybl.security.LimitViolationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gridsuite.loadflow.server.entities.LimitViolationEmbeddable;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Limit violation infos")
public class LimitViolationInfos {
    private String subjectId;

    private Double limit;

    private String limitName;

    private Integer actualOverload;

    private Integer upComingOverload;

    private Double value;

    private String side;

    private LimitViolationType limitType;

    public LimitViolationEmbeddable toEmbeddable() {
        return new LimitViolationEmbeddable(subjectId, limit, limitName, actualOverload, upComingOverload, value, side, limitType);
    }

    public static LimitViolationInfos toLimitViolationInfos(LimitViolationEmbeddable limitViolationEmbeddable) {
        return LimitViolationInfos.builder()
            .subjectId(limitViolationEmbeddable.getSubjectId())
            .limit(limitViolationEmbeddable.getLimit())
            .limitName(limitViolationEmbeddable.getLimitName())
            .actualOverload(limitViolationEmbeddable.getActualOverload())
            .upComingOverload(limitViolationEmbeddable.getUpComingOverload())
            .value(limitViolationEmbeddable.getValue())
            .side(limitViolationEmbeddable.getSide())
            .limitType(limitViolationEmbeddable.getLimitType())
            .actualOverload(limitViolationEmbeddable.getActualOverload())
            .upComingOverload(limitViolationEmbeddable.getUpComingOverload())
            .build();
    }
}
