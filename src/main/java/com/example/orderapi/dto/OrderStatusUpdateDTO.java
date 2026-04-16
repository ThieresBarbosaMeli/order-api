package com.example.orderapi.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderStatusUpdateDTO(
        @NotBlank(message = "Status não pode ser nulo ou vazio.")
        String status
) {}