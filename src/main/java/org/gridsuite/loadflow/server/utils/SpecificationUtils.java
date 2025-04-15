
/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.loadflow.server.utils;

import com.powsybl.ws.commons.computation.dto.ResourceFilterDTO;
import jakarta.persistence.criteria.*;
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
                                    ResourceFilterDTO filter) {

        String dotSeparatedField = filter.column();
        addPredicate(criteriaBuilder, path, predicates, filter, dotSeparatedField);
    }

    public static void addPredicate(CriteriaBuilder criteriaBuilder,
                                    Path<?> path,
                                    List<Predicate> predicates,
                                    ResourceFilterDTO filter,
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
                                              ResourceFilterDTO filter,
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
     * @throws UnsupportedOperationException if {@link ResourceFilterDTO.DataType filter.type} not supported or {@code filter.value} is {@code null}
     */
    public static Predicate filterToAtomicPredicate(CriteriaBuilder criteriaBuilder, Expression<?> expression, ResourceFilterDTO filter, Object value) {
        if (ResourceFilterDTO.DataType.TEXT == filter.dataType()) {
            return createTextPredicate(criteriaBuilder, expression, filter, (String) value);
        }
        if (ResourceFilterDTO.DataType.NUMBER == filter.dataType()) {
            return createNumberPredicate(criteriaBuilder, expression, filter.type(), (String) value, filter.tolerance());
        }
        throw new IllegalArgumentException("The filter type " + filter.type() + " is not supported with the data type " + filter.dataType());
    }

    private static Predicate createTextPredicate(CriteriaBuilder criteriaBuilder, Expression<?> expression, ResourceFilterDTO filter, String value) {
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

    private static Predicate createNumberPredicate(CriteriaBuilder criteriaBuilder,
                                                   Expression<?> expression,
                                                   ResourceFilterDTO.Type comparator,
                                                   String filterValue,
                                                   Double filterTolerance) {
        double tolerance;
        if (filterTolerance != null) {
            tolerance = filterTolerance;
        } else {
            // the reference for the comparison is the number of digits after the decimal point in filterValue
            // extra digits are ignored, but the user may add '0's after the decimal point in order to get a better precision
            String[] splitValue = filterValue.split("\\.");
            int numberOfDecimalAfterDot = 0;
            if (splitValue.length > 1) {
                numberOfDecimalAfterDot = splitValue[1].length();
            }
            // tolerance is multiplied by 0.5 to simulate the fact that the database value is rounded (in the front, from the user viewpoint)
            // more than 13 decimal after dot will likely cause rounding errors due to double precision
            tolerance = Math.pow(10, -numberOfDecimalAfterDot) * 0.5;
        }
        double filterValueDouble = Double.parseDouble(filterValue);
        Expression<Double> doubleExpression = expression.as(Double.class);

        return switch (comparator) {
            case NOT_EQUAL -> {
                Double upperBound = filterValueDouble + tolerance;
                Double lowerBound = filterValueDouble - tolerance;
                /**
                 * in order to be equal to doubleExpression, value has to fit :
                 * value - tolerance <= doubleExpression <= value + tolerance
                 * therefore in order to be different at least one of the opposite comparison needs to be true :
                 */
                yield criteriaBuilder.or(
                        criteriaBuilder.greaterThan(doubleExpression, upperBound),
                        criteriaBuilder.lessThan(doubleExpression, lowerBound)
                );
            }
            case LESS_THAN_OR_EQUAL -> criteriaBuilder.lessThanOrEqualTo(doubleExpression, filterValueDouble + tolerance);
            case GREATER_THAN_OR_EQUAL ->
                    criteriaBuilder.greaterThanOrEqualTo(doubleExpression, filterValueDouble - tolerance);
            default -> throw new UnsupportedOperationException("Unsupported filter type for number data type");
        };
    }
}
