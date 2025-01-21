package com.eshop.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
public class CartResponse {
    private List<CartItemDTO> items;
    private BigDecimal total;

    @Getter @Setter
    public static class CartItemDTO {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }
}