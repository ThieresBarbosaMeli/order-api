package com.example.orderapi.controller;

import com.example.orderapi.repository.IdempotencyRepository;
import com.example.orderapi.repository.OrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dev")
@Profile("dev")
public class DevController {

    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;

    public DevController(OrderRepository orderRepository, IdempotencyRepository idempotencyRepository) {
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    @DeleteMapping("/reset")
    public ResponseEntity<String> reset() {
        idempotencyRepository.deleteAll();
        orderRepository.deleteAll();
        return ResponseEntity.ok("Banco zerado com sucesso.");
    }
}
