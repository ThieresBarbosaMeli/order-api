package com.example.orderapi.dto;

public record OrderRequestDTO(
        String cpf_client,
        PaymentDTO payment,
        Long id_produto
) {}