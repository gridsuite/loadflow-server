/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories;

import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com
 */
@Repository
public interface ResultRepository extends JpaRepository<LoadFlowResultEntity, UUID> {
    Optional<LoadFlowResultEntity> findByResultUuid(UUID resultUuid);

    void deleteByResultUuid(UUID resultUuid);
}
