/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories;

import com.powsybl.loadflow.LoadFlowResult;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;


/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com
 */
@Repository
public interface ComponentResultRepository extends JpaRepository<ComponentResultEntity, UUID>, JpaSpecificationExecutor<ComponentResultEntity> {
    List<ComponentResultEntity> findAll(Specification specification, Sort sort);

    @Query(value = "SELECT distinct l.status from ComponentResultEntity as l " +
            "where l.loadFlowResult.resultUuid = :resultUuid AND l.status != ''" +
            "order by l.status")
    List<LoadFlowResult.ComponentResult.Status> findComputingStatus(UUID resultUuid);
}
