package com.example.orderapi.dto;

import com.example.orderapi.validation.ValidCPF;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderRequestDTO(
        @NotBlank(message = "CPF não pode ser vazio.")
        @Size(min = 11, max = 11, message = "CPF deve ter exatamente 11 caracteres.")
        @ValidCPF
        String cpf_client,

        @NotNull(message = "Pagamento não pode ser nulo.")
        @Valid
        PaymentDTO payment,

        @NotEmpty(message = "A lista de itens não pode ser vazia.")
        @Valid
        List<OrderItemDTO> items
) {}