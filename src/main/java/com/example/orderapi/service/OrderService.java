package com.example.orderapi.service;

import com.example.orderapi.domain.IdempotencyRecord;
import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderItem;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.domain.Payment;
import com.example.orderapi.domain.PaymentType;
import com.example.orderapi.dto.OrderRequestDTO;
import com.example.orderapi.dto.OrderStatusUpdateDTO;
import com.example.orderapi.exception.InvalidStatusTransitionException;
import com.example.orderapi.exception.OrderNotFoundException;
import com.example.orderapi.repository.IdempotencyRepository;
import com.example.orderapi.repository.OrderRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final IdempotencyRepository idempotencyRepository;

    public OrderService(OrderRepository repository, IdempotencyRepository idempotencyRepository) {
        this.repository = repository;
        this.idempotencyRepository = idempotencyRepository;
    }

    public Order create(OrderRequestDTO dto) {
        Order order = new Order();
        order.setCpfClient(dto.cpf_client());

        Payment payment = new Payment();
        payment.setType(PaymentType.valueOf(dto.payment().type()));
        payment.setPrice(dto.payment().price());
        order.setPayment(payment);

        dto.items().forEach(itemDTO -> {
            OrderItem item = new OrderItem();
            item.setIdProduct(itemDTO.idProduct());
            item.setQuantity(itemDTO.quantity());
            item.setPrice(itemDTO.price());
            item.setOrder(order);
            order.getItems().add(item);
        });

        return repository.save(order);
    }

    public Order pay(Long id, String idempotencyKey) {
        var existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return repository.findById(existing.get().getOrderId())
                    .orElseThrow(() -> new OrderNotFoundException("Order não encontrada com id: " + id));
        }

        Order order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order não encontrada com id: " + id));

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidStatusTransitionException(
                    "Pedido não pode ser pago. Status atual: " + order.getStatus()
            );
        }

        order.setStatus(OrderStatus.PAID);
        Order updated = repository.save(order);

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setOrderId(id);
        idempotencyRepository.save(record);

        return updated;
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

    public Page<Order> listAll(OrderStatus status, Pageable pageable) {
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        return repository.findAll(pageable);
    }
}