package com.example.orderapi.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

@Embeddable
public class Payment {

    @Enumerated(EnumType.STRING)
    private PaymentType type;

    private BigDecimal price;

    public PaymentType getType() { return type; }
    public void setType(PaymentType type) { this.type = type; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}