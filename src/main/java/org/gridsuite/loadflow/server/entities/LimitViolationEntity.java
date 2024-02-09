/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server.entities;

import com.powsybl.security.LimitViolationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Entity
@Table(name = "limitViolation", indexes = {
    @Index(name = "limitViolation_resultUuid_idx", columnList = "resultUuid")
})
public class LimitViolationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "resultUuid")
    private LoadFlowResultEntity loadFlowResult;

    @Column
    private String subjectId;

    @Column(name = "limit_")
    private Double limit;

    @Column
    private String limitName;

    @Column
    private Integer actualOverload;

    @Column
    private Integer upComingOverload;

    @Column
    private Double overload;

    @Column(name = "value_")
    private Double value;

    @Column
    private String side;

    @Column
    @Enumerated(EnumType.STRING)
    private LimitViolationType limitType;

}
