
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

        String dotSeparatedField = columnToDotSeparatedField(filter.column());
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
            String stringValue = (String) value;
            String escapedFilterValue = EscapeCharacter.DEFAULT.escape(stringValue);
            if (escapedFilterValue == null) {
                throw new UnsupportedOperationException("Filter text values can not be null");
            }
            // this makes equals query work with enum values
            Expression<String> stringExpression = expression.as(String.class);
            return switch (filter.type()) {
                case CONTAINS -> criteriaBuilder.like(criteriaBuilder.upper(stringExpression), "%" + escapedFilterValue.toUpperCase() + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
                case STARTS_WITH -> criteriaBuilder.like(criteriaBuilder.upper(stringExpression), escapedFilterValue.toUpperCase() + "%", EscapeCharacter.DEFAULT.getEscapeCharacter());
                case EQUALS -> criteriaBuilder.equal(criteriaBuilder.upper(stringExpression), stringValue.toUpperCase());
                default ->
                        throw new UnsupportedOperationException("This type of filter is not supported for text data type");
            };
        }

        if (ResourceFilter.DataType.NUMBER == filter.dataType()) {
            Double valueDouble = Double.valueOf((String) value);
            return switch (filter.type()) {
                case NOT_EQUAL -> criteriaBuilder.notEqual(expression, valueDouble);
                case LESS_THAN_OR_EQUAL -> criteriaBuilder.lessThanOrEqualTo((Expression<Double>) expression, valueDouble);
                case GREATER_THAN_OR_EQUAL ->
                        criteriaBuilder.greaterThanOrEqualTo((Expression<Double>) expression, valueDouble);
                default ->
                        throw new UnsupportedOperationException("This type of filter is not supported for number data type");
            };
        }
        throw new IllegalArgumentException("The filter type " + filter.type() + " is not supported with the data type " + filter.dataType());

    }

    static String columnToDotSeparatedField(ResourceFilter.Column column) {
        return switch (column) {
            case SUBJECT_ID -> "subjectId";
            case LIMIT -> "limit";
            case LIMIT_NAME -> "limitName";
            case LIMIT_TYPE -> "limitType";
            case ACTUEL_OVERLOAD -> "actualOverload";
            case UP_COMING_OVERLOAD -> "upComingOverload";
            case OVERLOAD -> "overload";
            case VALUE -> "value";
            case SIDE -> "side";
            case CONNECTED_COMPONENT_NUM -> "connectedComponentNum";
            case SYNCHRONOUS_COMPONENT_NUM -> "synchronousComponentNum";
            case STATUS -> "status";
            case ITERATION_COUNT -> "iterationCount";
            case SLACK_BUS_ID -> "slackBusId";
            case SLACK_BUS_ID_ACTIVE_POWER_MISMATCH -> "slackBusActivePowerMismatch";
            case DISTRIBUTED_ACTIVE_POWER -> "distributedActivePower";
            default -> throw new LoadflowException(LoadflowException.Type.INVALID_FILTER);
        };
    }
}
