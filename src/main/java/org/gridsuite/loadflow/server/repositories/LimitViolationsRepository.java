/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories;

import org.gridsuite.loadflow.server.entities.LimitViolationsEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;


/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Repository
public interface LimitViolationsRepository extends JpaRepository<LimitViolationsEntity, UUID>, JpaSpecificationExecutor<LimitViolationsEntity> {

    @EntityGraph(attributePaths = {"limitViolations"}, type = EntityGraph.EntityGraphType.LOAD)
    List<LimitViolationsEntity> findAll(Specification specification, Sort sort);

    void deleteByResultUuid(UUID resultUuid);
}
