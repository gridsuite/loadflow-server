/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server.entities.parameters;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
@Table(name = "loadFlowSpecificParameters", indexes = {
    @Index(name = "loadFlowParametersEntity_specificParameters_index", columnList = "load_flow_parameters_entity_id")
})
public class LoadFlowSpecificParameterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider")
    private String provider;

    @Column(name = "name")
    private String name;

    @Column(name = "value")
    private String value;
}
