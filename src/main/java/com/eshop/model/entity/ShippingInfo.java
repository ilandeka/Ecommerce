package com.eshop.model.entity;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter @Setter
public class ShippingInfo {
    private String fullName;
    private String address;
    private String city;
    private String state;
    private String zipCode;
}