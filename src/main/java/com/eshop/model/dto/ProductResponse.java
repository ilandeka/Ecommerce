package com.eshop.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private boolean available;
    private String imageUrl;

    public ProductResponse(Long id, String name, String description, BigDecimal price, Integer stockQuantity, boolean available, String imageUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.available = available;
        this.imageUrl = imageUrl;
    }
}
