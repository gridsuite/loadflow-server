/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.entities;

import com.powsybl.loadflow.LoadFlowResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class ComponentResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID componentResultUuid;

    @Column
    int connectedComponentNum;

    @Column
    int synchronousComponentNum;

    @Column
    @Enumerated(EnumType.STRING)
    LoadFlowResult.ComponentResult.Status status;

    @Column
    int iterationCount;

    @Column
    String slackBusId;

    @Column
    double slackBusActivePowerMismatch;

    @Column
    double distributedActivePower;

    @ManyToOne
    @JoinColumn(name = "resultUuid")
    private LoadFlowResultEntity loadFlowResult;

}
