package com.example.orderapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentDTO(
        @NotBlank(message = "Tipo de pagamento não pode ser vazio.")
        String type,

        @NotNull(message = "Valor do pagamento não pode ser nulo.")
        @Positive(message = "Valor do pagamento deve ser positivo.")
        BigDecimal price
) {}