/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.loadflow.server.utils;

import jakarta.persistence.criteria.*;
import org.gridsuite.loadflow.server.dto.ResourceFilter;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.function.Predicate.not;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
public final class SpecificationBuilder {

    private static final String LOADFLOW_RESULT_FIELD_NAME = "loadFlowResult";
    private static final String RESULT_UUID_FIELD_NAME = "resultUuid";
    private static final String SLACK_BUS_RESULTS = "slackBusResults";
    private static final String COMPONENT_RESULT = "componentResult";
    private static final String COMPONENT_RESULT_UUID = "componentResultUuid";

    // Utility class, so no constructor
    private SpecificationBuilder() {
    }

    private static <T> Specification<T> buildSpecifications(
            UUID uuid,
            List<ResourceFilter> filters,
            String fieldName,
            String uuidFieldName,
            boolean withJoin
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            // user filters on main entity
            filters.stream().filter(SpecificationBuilder::isParentFilter).forEach(filter -> SpecificationUtils.addPredicate(criteriaBuilder, root, predicates, filter));
            predicates.add(root.get(fieldName).get(uuidFieldName).in(List.of(uuid)));
            List<ResourceFilter> childrenFilters = filters.stream().filter(resourceFilter -> !isParentFilter(resourceFilter)).toList();
            if (withJoin) {
                if (!childrenFilters.isEmpty()) {
                    // user filters on OneToMany collection - needed here to filter main entities that would have empty collection when filters are applied
                    childrenFilters
                            .forEach(filter -> SpecificationUtils.addPredicate(criteriaBuilder, root.get(SLACK_BUS_RESULTS), predicates, filter));
                } else {
                    // filter parents with empty children even if there isn't any filter
                    predicates.add(criteriaBuilder.isNotEmpty(root.get(SLACK_BUS_RESULTS)));
                }
                // since sql joins generates duplicate results, we need to use distinct here
                query.distinct(true);
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<LimitViolationEntity> buildLimitViolationsSpecifications(
            UUID limitViolationsUuid,
            List<ResourceFilter> filters
    ) {
        return buildSpecifications(limitViolationsUuid, filters, LOADFLOW_RESULT_FIELD_NAME, RESULT_UUID_FIELD_NAME, false);
    }

    public static Specification<ComponentResultEntity> buildLoadflowResultSpecifications(
            UUID componentResultUuid,
            List<ResourceFilter> filters
    ) {
        return buildSpecifications(componentResultUuid, filters, LOADFLOW_RESULT_FIELD_NAME, RESULT_UUID_FIELD_NAME, true);
    }

    public static boolean isParentFilter(ResourceFilter filter) {
        return !List.of(ResourceFilter.Column.ID, ResourceFilter.Column.ACTIVE_POWER_MISMATCH).contains(filter.column());
    }

    public static <T> Specification<T> getSlackBusResultsSpecifications(
           List<UUID> componentResultUuids,
            List<ResourceFilter> filters
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            filters.stream().filter(not(SpecificationBuilder::isParentFilter))
                    .forEach(filter -> SpecificationUtils.addPredicate(criteriaBuilder, root, predicates, filter));

            predicates.add(root.get(COMPONENT_RESULT).get(COMPONENT_RESULT_UUID).in(componentResultUuids));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
