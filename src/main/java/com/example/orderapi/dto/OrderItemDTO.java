package com.example.orderapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderItemDTO(
        @NotNull(message = "ID do produto não pode ser nulo.")
        Long idProduct,

        @NotNull(message = "Quantidade não pode ser nula.")
        @Positive(message = "Quantidade deve ser positiva.")
        Integer quantity,

        @NotNull(message = "Preço não pode ser nulo.")
        @Positive(message = "Preço deve ser positivo.")
        BigDecimal price
) {}