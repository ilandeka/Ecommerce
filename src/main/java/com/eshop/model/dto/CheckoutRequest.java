package com.eshop.model.dto;

import com.eshop.model.entity.ShippingInfo;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CheckoutRequest {
    private ShippingInfo shippingInfo;
}