package org.gridsuite.loadflow.server.utils;

import jakarta.persistence.criteria.*;
import org.gridsuite.loadflow.server.dto.ResourceFilter;
import org.gridsuite.loadflow.server.entities.LimitViolationsEntity;
import org.gridsuite.loadflow.server.entities.LoadFlowResultEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpecificationBuilder {

    private static final String ID_FIELD_NAME = "resultUuid";

    // Utility class, so no constructor
    private SpecificationBuilder() {
    }

    public static Specification<LimitViolationsEntity> buildLimitViolationsSpecifications(
            UUID limitViolationsUuid,
            List<ResourceFilter> filters
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            filters.stream().forEach(filter -> SpecificationUtils.addPredicate(criteriaBuilder, root.get("limitViolations"), predicates, filter));
            predicates.add(root.get(ID_FIELD_NAME).in(List.of(limitViolationsUuid)));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<LoadFlowResultEntity> buildLoadflowResultSpecifications(
            UUID limitViolationsUuid,
            List<ResourceFilter> filters
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            filters.stream().forEach(filter -> SpecificationUtils.addPredicate(criteriaBuilder, root.get("componentResults"), predicates, filter));
            predicates.add(root.get(ID_FIELD_NAME).in(List.of(limitViolationsUuid)));
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
