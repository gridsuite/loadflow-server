/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories.specifications;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.specification.AbstractCommonSpecificationBuilder;
import jakarta.persistence.criteria.*;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@Service
public final class LimitViolationsSpecificationBuilder extends AbstractCommonSpecificationBuilder<LimitViolationEntity> {
    public static final String RESULT_UUID_FIELD_NAME = "resultUuid";

    @Override
    public Specification<LimitViolationEntity> addSpecificFilterWhenChildrenFilters() {
        return null;
    }

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

    @Override
    public Specification<LimitViolationEntity> addSpecificFilterWhenNoChildrenFilter() {
        return null;
    }
}
