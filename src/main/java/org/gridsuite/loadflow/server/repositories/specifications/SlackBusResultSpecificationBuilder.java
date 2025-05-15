/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.repositories.specifications;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import com.powsybl.ws.commons.computation.specification.AbstractCommonSpecificationBuilder;
import com.powsybl.ws.commons.computation.utils.SpecificationUtils;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.gridsuite.loadflow.server.dto.Column;
import org.gridsuite.loadflow.server.entities.SlackBusResultEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SlackBusResultSpecificationBuilder extends AbstractCommonSpecificationBuilder<SlackBusResultEntity> {
    private static final String COMPONENT_RESULT = "componentResult";
    private static final String COMPONENT_RESULT_UUID = "componentResultUuid";

    @Override
    public Specification<SlackBusResultEntity> uuidIn(List<UUID> uuids) {
        return (root, cq, cb) -> root.get(COMPONENT_RESULT).get(COMPONENT_RESULT_UUID).in(uuids);
    }

    @Override
    public Specification<SlackBusResultEntity> addSpecificFilterWhenChildrenFilters() {
        return null;
    }

    @Override
    public String getIdFieldName() {
        return SlackBusResultEntity.Fields.slackBusResulttUuid;
    }

    @Override
    public boolean isNotParentFilter(ResourceFilterDTO filter) {
        return List.of(Column.ID.columnName(), Column.ACTIVE_POWER_MISMATCH.columnName())
                .contains(filter.column());
    }

    public Specification<SlackBusResultEntity> buildSlackBusResultSpecification(List<UUID> uuids, List<ResourceFilterDTO> resourceFilters) {
        List<ResourceFilterDTO> childrenResourceFilters = resourceFilters.stream().filter(this::isNotParentFilter).toList();
        Specification<SlackBusResultEntity> specification = Specification.where(uuidIn(uuids));

        return SpecificationUtils.appendFiltersToSpecification(specification, childrenResourceFilters);
    }

    @Override
    public Path<UUID> getResultIdPath(Root<SlackBusResultEntity> root) {
        return root.get(COMPONENT_RESULT).get(COMPONENT_RESULT_UUID);
    }

    @Override
    public Specification<SlackBusResultEntity> addSpecificFilterWhenNoChildrenFilter() {
        return this.addSpecificFilterWhenChildrenFilters();
    }
}
