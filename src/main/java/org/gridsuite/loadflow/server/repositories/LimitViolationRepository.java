/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.security.LimitViolationType;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
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
public interface LimitViolationRepository extends JpaRepository<LimitViolationEntity, UUID>, JpaSpecificationExecutor<LimitViolationEntity> {

    boolean existsLimitViolationEntitiesByLoadFlowResultResultUuid(UUID resultUuid);

    @Query(value = "SELECT distinct l.limitType from LimitViolationEntity as l " +
            "where l.loadFlowResult.resultUuid = :resultUuid AND l.limitType != '' AND l.limitType != 'CURRENT'" +
            "order by l.limitType")
    List<LimitViolationType> findLimitTypes(UUID resultUuid);

    @Query(value = "SELECT distinct l.side from LimitViolationEntity as l " +
            "where l.loadFlowResult.resultUuid = :resultUuid AND l.side != ''" +
            "order by l.side")
    List<TwoSides> findBranchSides(UUID resultUuid);
}
