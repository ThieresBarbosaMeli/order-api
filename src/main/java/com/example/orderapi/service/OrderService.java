package com.example.orderapi.service;

import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.domain.Payment;
import com.example.orderapi.domain.PaymentType;
import com.example.orderapi.dto.OrderRequestDTO;
import com.example.orderapi.dto.OrderStatusUpdateDTO;
import com.example.orderapi.exception.InvalidStatusTransitionException;
import com.example.orderapi.exception.OrderNotFoundException;
import com.example.orderapi.repository.OrderRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;

@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order create(OrderRequestDTO dto) {
        validateCreateRequest(dto);

        Order order = new Order();
        order.setCpfClient(dto.cpf_client());
        order.setIdProduct(dto.id_produto());

        Payment payment = new Payment();
        payment.setType(PaymentType.valueOf(dto.payment().type()));
        payment.setPrice(dto.payment().price());
        order.setPayment(payment);

        return repository.save(order);
    }

    @CacheEvict(value = "orders", key = "#id")
    public Order updateStatus(Long id, OrderStatusUpdateDTO dto) {
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(dto.status());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status inválido: " + dto.status());
        }

        Order order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order não encontrada com id: " + id));

        if (!order.getStatus().isValidNext(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Transição inválida: " + order.getStatus() + " -> " + newStatus
            );
        }

        order.setStatus(newStatus);
        return repository.save(order);
    }

    @Cacheable(value = "orders", key = "#id")
    public Order getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order não encontrada com id: " + id));
    }

    private void validateCreateRequest(OrderRequestDTO dto) {
        if (dto.cpf_client() == null || dto.cpf_client().length() != 11) {
            throw new IllegalArgumentException("CPF deve ter exatamente 11 caracteres.");
        }
        if (dto.payment() == null) {
            throw new IllegalArgumentException("Pagamento não pode ser nulo ou vazio.");
        }
        if (dto.payment().type() == null || !isValidPaymentType(dto.payment().type())) {
            throw new IllegalArgumentException("Tipo de pagamento deve ser PIX, BOLETO ou CARTAO.");
        }
        if (dto.payment().price() == null || dto.payment().price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor do pagamento deve ser positivo.");
        }
        if (dto.id_produto() == null) {
            throw new IllegalArgumentException("ID do produto não pode ser nulo.");
        }
    }

    private boolean isValidPaymentType(String type) {
        return Arrays.stream(PaymentType.values())
                .anyMatch(p -> p.name().equals(type));
    }
}