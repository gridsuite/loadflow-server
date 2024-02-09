
package org.gridsuite.loadflow.server.utils;

import jakarta.persistence.criteria.*;
import org.gridsuite.loadflow.server.dto.ResourceFilter;
import org.gridsuite.loadflow.server.entities.ComponentResultEntity;
import org.gridsuite.loadflow.server.entities.LimitViolationEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpecificationBuilder {

    private static final String LOADFLOW_RESULT_FIELD_NAME = "loadFlowResult";
    private static final String RESULT_UUID_FIELD_NAME = "resultUuid";

    // Utility class, so no constructor
    private SpecificationBuilder() {
    }

    private static <T> Specification<T> buildSpecifications(
            UUID uuid,
            List<ResourceFilter> filters,
            String fieldName,
            String uuidFieldName
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            filters.forEach(filter -> SpecificationUtils.addPredicate(criteriaBuilder, root, predicates, filter));
            predicates.add(root.get(fieldName).get(uuidFieldName).in(List.of(uuid)));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<LimitViolationEntity> buildLimitViolationsSpecifications(
            UUID limitViolationsUuid,
            List<ResourceFilter> filters
    ) {
        return buildSpecifications(limitViolationsUuid, filters, LOADFLOW_RESULT_FIELD_NAME, RESULT_UUID_FIELD_NAME);
    }

    public static Specification<ComponentResultEntity> buildLoadflowResultSpecifications(
            UUID limitViolationsUuid,
            List<ResourceFilter> filters
    ) {
        return buildSpecifications(limitViolationsUuid, filters, LOADFLOW_RESULT_FIELD_NAME, RESULT_UUID_FIELD_NAME);
    }
}
