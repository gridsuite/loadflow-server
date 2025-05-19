/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories.specifications;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.utils.specification.AbstractCommonSpecificationBuilder;
import com.powsybl.ws.commons.computation.utils.specification.SpecificationUtils;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.loadflow.server.dto.Column;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.loadflow.server.repositories.specifications.LimitViolationsSpecificationBuilder.RESULT_UUID_FIELD_NAME;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com>
 */
@Service
public class ComponentResultSpecificationBuilder extends AbstractCommonSpecificationBuilder<ComponentResultEntity> {

    @Override
    public Specification<ComponentResultEntity> addSpecificFilterWhenChildrenFilters() {
        return SpecificationUtils.isNotEmpty(ComponentResultEntity.Fields.slackBusResults);
    }

    @Override
    public boolean isNotParentFilter(ResourceFilterDTO filter) {
        return List.of(Column.ID.columnName(), Column.ACTIVE_POWER_MISMATCH.columnName())
                .contains(filter.column());
    }

    @Override
    public String getIdFieldName() {
        return LimitViolationEntity.Fields.id;
    }

    @Override
    public Path<UUID> getResultIdPath(Root<ComponentResultEntity> root) {
        return root.get(LimitViolationEntity.Fields.loadFlowResult).get(RESULT_UUID_FIELD_NAME);
    }

    @Override
    public Specification<ComponentResultEntity> addSpecificFilterWhenNoChildrenFilter() {
        return this.addSpecificFilterWhenChildrenFilters();
    }
}
