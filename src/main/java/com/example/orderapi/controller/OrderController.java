package com.example.orderapi.controller;

import com.example.orderapi.domain.Order;
import com.example.orderapi.dto.OrderRequestDTO;
import com.example.orderapi.dto.OrderStatusUpdateDTO;
import com.example.orderapi.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody OrderRequestDTO dto) {
        Order order = service.create(dto);
        return ResponseEntity.status(201).body(order);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Order> updateStatus(
            @PathVariable Long id,
            @RequestBody OrderStatusUpdateDTO dto
    ) {
        Order order = service.updateStatus(id, dto);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getById(@PathVariable Long id) {
        Order order = service.getById(id);
        return ResponseEntity.ok(order);
    }
}