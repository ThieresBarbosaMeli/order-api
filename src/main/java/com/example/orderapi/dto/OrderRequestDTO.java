package com.example.orderapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderRequestDTO(
        @NotBlank(message = "CPF não pode ser vazio.")
        @Size(min = 11, max = 11, message = "CPF deve ter exatamente 11 caracteres.")
        String cpf_client,

        @NotNull(message = "Pagamento não pode ser nulo.")
        @Valid
        PaymentDTO payment,

        @NotNull(message = "ID do produto não pode ser nulo.")
        Long id_produto
) {}