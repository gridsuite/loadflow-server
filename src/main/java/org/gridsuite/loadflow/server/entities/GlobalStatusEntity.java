/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.loadflow.server.dto.LoadFlowStatus;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

/**
 * @author Anis TOURI <anis.touri at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "globalStatus")
public class GlobalStatusEntity implements Serializable {

    @Id
    private UUID resultUuid;

    @Enumerated(EnumType.STRING)
    private LoadFlowStatus status;


}
