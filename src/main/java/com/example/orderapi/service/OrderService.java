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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final CacheManager cacheManager;

    public OrderService(OrderRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
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
        order.setDateUpdated(LocalDateTime.now());
        Order updated = repository.save(order);

        Cache cache = cacheManager.getCache("orders");
        if (cache != null) {
            cache.evict(id);
        }

        return updated;
    }

    public Order getById(Long id) {
        Cache cache = cacheManager.getCache("orders");

        if (cache != null) {
            Cache.ValueWrapper cached = cache.get(id);
            if (cached != null) {
                return (Order) cached.get();
            }
        }

        Order order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order não encontrada com id: " + id));

        if (cache != null) {
            cache.put(id, order);
        }

        return order;
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