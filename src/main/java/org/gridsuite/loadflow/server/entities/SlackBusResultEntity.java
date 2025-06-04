/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldNameConstants;

import java.util.UUID;

/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@FieldNameConstants
@Entity
public class SlackBusResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID slackBusResulttUuid;
    @Column
    private String id;
    @Column
    private double activePowerMismatch;

    @ManyToOne
    @Setter
    @JoinColumn(name = "componentResultUuid")
    private ComponentResultEntity componentResult;

    public static SlackBusResultEntity toEntity(String id, double activePowerMismatch) {
        return SlackBusResultEntity.builder()
                .id(id)
                .activePowerMismatch(activePowerMismatch)
                .build();
    }
}
