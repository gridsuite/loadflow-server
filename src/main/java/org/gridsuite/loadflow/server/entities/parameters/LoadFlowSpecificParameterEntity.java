/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server.entities.parameters;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.gridsuite.loadflow.server.dto.parameters.LoadFlowSpecificParameterInfos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "loadFlowSpecificParameters")
public class LoadFlowSpecificParameterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider")
    private String provider;

    @Column(name = "name")
    private String name;

    @Column(name = "value_")
    private String value;

    public static List<LoadFlowSpecificParameterEntity> toLoadFlowSpecificParameters(List<LoadFlowSpecificParameterInfos> params) {
        return params == null ? null
            : params.stream()
            .map(p -> new LoadFlowSpecificParameterEntity(null, p.getProvider(), p.getName(), p.getValue()))
            .collect(Collectors.toList());
    }

    public LoadFlowSpecificParameterInfos toLoadFlowSpecificParameterInfos() {
        return LoadFlowSpecificParameterInfos.builder()
            .provider(getProvider())
            .name(getName())
            .value(getValue())
            .build();
    }
}
