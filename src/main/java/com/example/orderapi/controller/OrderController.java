package com.example.orderapi.controller;

import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.dto.OrderRequestDTO;
import com.example.orderapi.dto.OrderStatusUpdateDTO;
import com.example.orderapi.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody @Valid OrderRequestDTO dto) {
        Order order = service.create(dto);
        return ResponseEntity.status(201).body(order);
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<Order> pay(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        Order order = service.pay(id, idempotencyKey);
        return ResponseEntity.ok(order);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(
            @PathVariable Long id,
            @RequestBody @Valid OrderStatusUpdateDTO dto
    ) {
        Order order = service.updateStatus(id, dto);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable Long id) {
        Order order = service.getById(id);
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public ResponseEntity<Page<Order>> listAll(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(service.listAll(status, pageable));
    }
}