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
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;

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

    private String locationId;

    private Double limit;

    private String limitName;

    private String nextLimitName;

    private Integer actualOverloadDuration;

    private Integer upComingOverloadDuration;

    private Double overload;

    private Double patlLimit;

    private Double patlOverload;

    private Double value;

    private String side;

    private LimitViolationType limitType;

    public static LimitViolationInfos toLimitViolationInfos(LimitViolationEntity limitViolationEntity) {
        return LimitViolationInfos.builder()
                .subjectId(limitViolationEntity.getSubjectId())
                .locationId(limitViolationEntity.getLocationId())
                .limit(limitViolationEntity.getLimit())
                .limitName(limitViolationEntity.getLimitName())
                .nextLimitName(limitViolationEntity.getNextLimitName())
                .actualOverloadDuration(limitViolationEntity.getActualOverload())
                .upComingOverloadDuration(limitViolationEntity.getUpComingOverload())
                .overload(limitViolationEntity.getOverload())
                .patlLimit(limitViolationEntity.getPatlLimit())
                .patlOverload(limitViolationEntity.getPatlOverload())
                .value(limitViolationEntity.getValue())
                .side(limitViolationEntity.getSide())
                .limitType(limitViolationEntity.getLimitType())
                .build();
    }
}
