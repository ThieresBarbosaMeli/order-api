package com.example.orderapi.domain;

public enum OrderStatus {
    CREATED, PAID, SENT, DELIVERED;

    public boolean isValidNext(OrderStatus next) {
        return next.ordinal() > this.ordinal();
    }
}