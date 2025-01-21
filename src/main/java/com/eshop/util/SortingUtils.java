// Create a new utility class
package com.eshop.util;

import org.springframework.data.domain.Sort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SortingUtils {
    public static final Set<String> ALLOWED_ORDER_FIELDS = Set.of("createdAt", "total", "status");
    public static final Set<String> ALLOWED_PRODUCT_FIELDS = Set.of("name", "price", "createdAt");

    public static void validateSortField(String field, Set<String> allowedFields) {
        if (!allowedFields.contains(field)) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
        }
    }

    // Method 1: Simple single-field sorting
    public static Sort createSort(String sortParam) {
        String[] parts = sortParam.split(",");
        String field = parts[0];
        Sort.Direction direction = parts.length > 1 ?
                Sort.Direction.fromString(parts[1]) : Sort.Direction.DESC;

        return Sort.by(direction, field);
    }

    // Method 2: Multiple-field sorting
    public static List<Sort.Order> createSortOrders(String[] sortParams) {
        List<Sort.Order> orders = new ArrayList<>();

        if (sortParams[0].contains(",")) {
            for (String sortParam : sortParams) {
                String[] parts = sortParam.split(",");
                orders.add(new Sort.Order(
                        Sort.Direction.fromString(parts[1]),
                        parts[0]
                ));
            }
        } else {
            orders.add(new Sort.Order(Sort.Direction.DESC, sortParams[0]));
        }

        return orders;
    }
}