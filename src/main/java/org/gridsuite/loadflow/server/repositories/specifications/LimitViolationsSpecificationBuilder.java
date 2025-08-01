/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories.specifications;

import jakarta.persistence.criteria.*;
import org.gridsuite.computation.dto.ResourceFilterDTO;
import org.gridsuite.computation.specification.AbstractCommonSpecificationBuilder;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com>
 */
@Service
public final class LimitViolationsSpecificationBuilder extends AbstractCommonSpecificationBuilder<LimitViolationEntity> {
    public static final String RESULT_UUID_FIELD_NAME = "resultUuid";

    @Override
    public boolean isNotParentFilter(ResourceFilterDTO filter) {
        return !filter.column().equals(LimitViolationEntity.Fields.id);
    }

    @Override
    public String getIdFieldName() {
        return LimitViolationEntity.Fields.id;
    }

    @Override
    public Path<UUID> getResultIdPath(Root<LimitViolationEntity> root) {
        return root.get(LimitViolationEntity.Fields.loadFlowResult).get(RESULT_UUID_FIELD_NAME);
    }
}
