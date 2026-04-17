package com.example.orderapi.dto;

import com.example.orderapi.validation.ValidCPF;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderRequestDTO(
        @NotBlank(message = "CPF não pode ser vazio.")
        @ValidCPF
        String cpf_client,

        @NotNull(message = "Pagamento não pode ser nulo.")
        @Valid
        PaymentDTO payment,

        @NotNull(message = "ID do produto não pode ser nulo.")
        Long id_produto
) {}