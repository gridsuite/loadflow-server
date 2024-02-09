/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories;

import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Repository
public interface LimitViolationRepository extends JpaRepository<LimitViolationEntity, UUID>, JpaSpecificationExecutor<LimitViolationEntity> {
    //List<LimitViolationEntity> findByResultUuid(UUID resultUuid);

    void deleteByResultUuid(UUID resultUuid);

    boolean existsLimitViolationEntitiesByLoadFlowResultResultUuid(UUID resultUuid);
}
