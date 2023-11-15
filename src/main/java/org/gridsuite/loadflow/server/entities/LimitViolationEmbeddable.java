/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server.entities;

import com.powsybl.security.LimitViolationType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Embeddable
public class LimitViolationEmbeddable {
    @Column
    private String subjectId;

    @Column(name = "limit_")
    private Double limit;

    @Column
    private String limitName;

    @Column
    private Integer acceptableDuration;

    @Column(name = "value_")
    private Double value;

    @Column
    private String side;

    @Column
    @Enumerated(EnumType.STRING)
    private LimitViolationType limitType;

    @Column
    private Integer actualOverload;

    @Column
    private Integer upComingOverload;
}
