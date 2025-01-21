package com.example.ecommerce.model.dto;

import com.example.ecommerce.model.entity.ShippingInfo;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CheckoutRequest {
    private ShippingInfo shippingInfo;
}