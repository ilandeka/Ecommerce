package com.example.ecommerce.model.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShippingInfo {
    private String fullName;
    private String address;
    private String city;
    private String state;
    private String zipCode;
}
