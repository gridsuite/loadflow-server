/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories.parameters;

import java.util.UUID;

import org.gridsuite.loadflow.server.entities.parameters.LoadFlowParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */

@Repository
public interface LoadFlowParametersRepository extends JpaRepository<LoadFlowParametersEntity, UUID> {
}
