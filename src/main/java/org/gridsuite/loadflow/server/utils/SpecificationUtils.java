
/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server.utils;

import jakarta.persistence.criteria.*;
import org.gridsuite.loadflow.server.dto.ResourceFilter;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

/**
 * Utility class to create Spring Data JPA Specification (Spring interface for JPA Criteria API).
 *
 * @author Anis TOURI <anis.touri@rte-france.com>
 */
public final class SpecificationUtils {

    // Utility class, so no constructor
    private SpecificationUtils() {
    }

    public static void addPredicate(CriteriaBuilder criteriaBuilder,
                                    Path<?> path,
                                    List<Predicate> predicates,
                                    ResourceFilter filter) {

        String dotSeparatedField = filter.column().columnName();
        addPredicate(criteriaBuilder, path, predicates, filter, dotSeparatedField);
    }

    public static void addPredicate(CriteriaBuilder criteriaBuilder,
                                    Path<?> path,
                                    List<Predicate> predicates,
                                    ResourceFilter filter,
                                    String fieldName) {
        Predicate predicate = filterToPredicate(criteriaBuilder, path, filter, fieldName);
        if (predicate != null) {
            predicates.add(predicate);
        }
    }

    /**
     * Returns {@link Predicate} depending on {@code filter.value()} type:
     * if it's a {@link Collection}, it will use "OR" operator between each value
     */
    public static Predicate filterToPredicate(CriteriaBuilder criteriaBuilder,
                                              Path<?> path,
                                              ResourceFilter filter,
                                              String field) {
        // expression targets field to filter on
        Expression<String> expression = path.get(field);

        // collection values are filtered with "or" operator
        if (filter.value() instanceof Collection<?> filterCollection) {
            if (CollectionUtils.isEmpty(filterCollection)) {
                return null;
            }
            return criteriaBuilder.or(
                    filterCollection.stream().map(value ->
                            SpecificationUtils.filterToAtomicPredicate(criteriaBuilder, expression, filter, value)
                    ).toArray(Predicate[]::new)
            );
        } else {
            return SpecificationUtils.filterToAtomicPredicate(criteriaBuilder, expression, filter, filter.value());
        }
    }

    /**
     * Returns atomic {@link Predicate} depending on {@code filter.dataType()} and {@code filter.type()}
     * @throws UnsupportedOperationException if {@link ResourceFilter.DataType filter.type} not supported or {@code filter.value} is {@code null}
     */
    public static Predicate filterToAtomicPredicate(CriteriaBuilder criteriaBuilder, Expression<?> expression, ResourceFilter filter, Object value) {
        if (ResourceFilter.DataType.TEXT == filter.dataType()) {
            return createTextPredicate(criteriaBuilder, expression, filter, (String) value);
        }
        if (ResourceFilter.DataType.NUMBER == filter.dataType()) {
            return createNumberPredicate(criteriaBuilder, expression, filter, (String) value);
        }
        throw new IllegalArgumentException("The filter type " + filter.type() + " is not supported with the data type " + filter.dataType());
    }

    private static Predicate createTextPredicate(CriteriaBuilder criteriaBuilder, Expression<?> expression, ResourceFilter filter, String value) {
        String escapedFilterValue = EscapeCharacter.DEFAULT.escape(value);
        if (escapedFilterValue == null) {
            throw new UnsupportedOperationException("Filter text values can not be null");
        }
        // this makes equals query work with enum values
        Expression<String> stringExpression = expression.as(String.class);

        return switch (filter.type()) {
            case CONTAINS ->
                    criteriaBuilder.like(criteriaBuilder.upper(stringExpression), "%" + value.toUpperCase() + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
            case STARTS_WITH ->
                    criteriaBuilder.like(criteriaBuilder.upper(stringExpression), value.toUpperCase() + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
            case EQUALS, IN -> criteriaBuilder.equal(criteriaBuilder.upper(stringExpression), value.toUpperCase());
            default -> throw new UnsupportedOperationException("Unsupported filter type for text data type");
        };
    }

    private static Predicate createNumberPredicate(CriteriaBuilder criteriaBuilder, Expression<?> expression, ResourceFilter filter, String value) {
        int numberOfDecimalAfterDot = value.split("\\.").length > 1 ? value.split("\\.")[1].length() : 0;
        final double tolerance = Math.pow(10, -numberOfDecimalAfterDot); // tolerance for comparison
        Double valueDouble = Double.valueOf(value);
        Expression<Double> doubleExpression = expression.as(Double.class);

        return switch (filter.type()) {
            case NOT_EQUAL -> {
                Double upperBound = valueDouble + tolerance;
                Double lowerBound = valueDouble - tolerance;
                yield criteriaBuilder.or(criteriaBuilder.greaterThanOrEqualTo(doubleExpression, upperBound), criteriaBuilder.lessThanOrEqualTo(doubleExpression, lowerBound));
            }
            case LESS_THAN_OR_EQUAL -> criteriaBuilder.lessThanOrEqualTo(doubleExpression, valueDouble + tolerance);
            case GREATER_THAN_OR_EQUAL ->
                    criteriaBuilder.greaterThanOrEqualTo(doubleExpression, valueDouble - tolerance);
            default -> throw new UnsupportedOperationException("Unsupported filter type for number data type");
        };
    }
}
