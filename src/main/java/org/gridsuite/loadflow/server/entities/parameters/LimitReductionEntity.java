/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.entities.parameters;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor
@Table(name = "limitReductionEntity", indexes = {@Index(name = "idx_load_flow_parameters_id", columnList = "load_flow_parameters_id")})
public class LimitReductionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "limit_reduction_entity_reductions",
            joinColumns = @JoinColumn(name = "limit_reduction_entity_id"),
            foreignKey = @ForeignKey(name = "limitReductionEntity_limitReductionEntityReductions_fk")
    )
    @OrderColumn(name = "index")
    @Column(name = "reductions")
    private List<Double> reductions;

    public LimitReductionEntity(List<Double> reductions) {
        this.reductions = reductions;
    }
}
