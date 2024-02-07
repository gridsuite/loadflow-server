/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.loadflow.server.dto.LimitViolationInfos;
import org.gridsuite.loadflow.server.dto.LimitViolationsInfos;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "limitViolations")
public class LimitViolationsEntity {
    @Id
    private UUID resultUuid;

    @ElementCollection
    @CollectionTable(name = "limitViolation",
        indexes = {@Index(name = "LimitViolationsEntity_limits_idx1", columnList = "limit_violations_entity_result_uuid")},
        foreignKey = @ForeignKey(name = "LimitViolationsEntity_limits_fk1"))
    private List<LimitViolationEmbeddable> limitViolations;

    public LimitViolationsEntity(UUID resultUuid, List<LimitViolationInfos> limitViolationsInfos) {
        this.resultUuid = resultUuid;
        limitViolations = limitViolationsInfos.stream().map(LimitViolationInfos::toEmbeddable).collect(Collectors.toList());
    }

    private List<LimitViolationInfos> toLimitViolationsInfos(List<LimitViolationEmbeddable> limitViolations) {
        return limitViolations.stream().map(LimitViolationInfos::toLimitViolationInfos).collect(Collectors.toList());
    }

    public LimitViolationsInfos toLimitViolationsInfos() {
        return LimitViolationsInfos.builder()
            .limitViolations(toLimitViolationsInfos(limitViolations))
            .build();
    }
}
