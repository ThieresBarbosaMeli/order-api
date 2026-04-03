package com.example.orderapi.dto;

import java.math.BigDecimal;

public record PaymentDTO(String type, BigDecimal price) {}