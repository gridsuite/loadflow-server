/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.entities;

import com.powsybl.loadflow.LoadFlowResult;
import lombok.*;

import jakarta.persistence.*;
import lombok.experimental.FieldNameConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
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
    double distributedActivePower;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "componentResult", fetch = FetchType.LAZY)
    private List<SlackBusResultEntity> slackBusResults = new ArrayList<>();

    @Column
    Double consumptions;

    @Column
    Double generations;

    @Column
    Double exchanges;

    @Column
    Double losses;

    @ManyToOne
    @JoinColumn(name = "resultUuid")
    private LoadFlowResultEntity loadFlowResult;

    public void setSlackBusResults(List<SlackBusResultEntity> slackBusResults) {
        if (slackBusResults != null) {
            this.slackBusResults = slackBusResults;
            slackBusResults.forEach(slackBusResult -> slackBusResult.setComponentResult(this));
        }
    }

}
