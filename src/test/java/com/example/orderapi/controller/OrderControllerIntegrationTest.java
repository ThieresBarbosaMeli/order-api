package com.example.orderapi.controller;

import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.domain.Payment;
import com.example.orderapi.domain.PaymentType;
import com.example.orderapi.repository.IdempotencyRepository;
import com.example.orderapi.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void postOrders_deveCriarPedidoERetornar201() throws Exception {
        String body = """
                {
                  "cpf_client": "12345678901",
                  "payment": { "type": "PIX", "price": 100.00 },
                  "id_produto": 1
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.cpf_client").value("12345678901"));
    }

    @Test
    void postOrders_deveRetornar400SeCpfInvalido() throws Exception {
        String body = """
                {
                  "cpf_client": "123",
                  "payment": { "type": "PIX", "price": 100.00 },
                  "id_produto": 1
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.cpf_client").exists());
    }

    @Test
    void postOrders_deveRetornar400SePagamentoNulo() throws Exception {
        String body = """
                {
                  "cpf_client": "12345678901",
                  "id_produto": 1
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postOrdersPay_devePagarPedidoComSucesso() throws Exception {
        Order order = criarPedido();

        mockMvc.perform(post("/orders/{id}/pay", order.getId())
                        .header("Idempotency-Key", "chave-unica-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void postOrdersPay_deveSerIdempotente() throws Exception {
        Order order = criarPedido();

        mockMvc.perform(post("/orders/{id}/pay", order.getId())
                        .header("Idempotency-Key", "chave-unica-002"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/{id}/pay", order.getId())
                        .header("Idempotency-Key", "chave-unica-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void patchOrdersStatus_deveAtualizarStatusComSucesso() throws Exception {
        Order order = criarPedido();
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        String body = """
                { "status": "SENT" }
                """;

        mockMvc.perform(patch("/orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void patchOrdersStatus_deveRetornar409ParaTransicaoInvalida() throws Exception {
        Order order = criarPedido();

        String body = """
                { "status": "DELIVERED" }
                """;

        mockMvc.perform(patch("/orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void patchOrdersStatus_deveRetornar404SePedidoNaoExiste() throws Exception {
        String body = """
                { "status": "PAID" }
                """;

        mockMvc.perform(patch("/orders/{id}/status", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrdersById_deveRetornarPedidoComSucesso() throws Exception {
        Order order = criarPedido();

        mockMvc.perform(get("/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.cpf_client").value("12345678901"));
    }

    @Test
    void getOrdersById_deveRetornar404SePedidoNaoExiste() throws Exception {
        mockMvc.perform(get("/orders/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrders_deveListarPedidosComPaginacao() throws Exception {
        criarPedido();
        criarPedido();

        mockMvc.perform(get("/orders?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void getOrders_deveFilterarPorStatus() throws Exception {
        criarPedido();

        mockMvc.perform(get("/orders?status=CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CREATED"));
    }

    @Test
    void patchOrdersStatus_deveRetornar400ComStatusNulo() throws Exception {
        Order order = criarPedido();

        String body = """
                { "status": null }
                """;

        mockMvc.perform(patch("/orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchOrdersStatus_deveRetornar400ComStatusVazio() throws Exception {
        Order order = criarPedido();

        String body = """
                { "status": "" }
                """;

        mockMvc.perform(patch("/orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postOrdersPay_deveRetornar409ComChaveUsadaParaOutroPedido() throws Exception {
        Order order1 = criarPedido();
        Order order2 = criarPedido();

        mockMvc.perform(post("/orders/{id}/pay", order1.getId())
                        .header("Idempotency-Key", "chave-conflito-001"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/{id}/pay", order2.getId())
                        .header("Idempotency-Key", "chave-conflito-001"))
                .andExpect(status().isConflict());
    }

    private Order criarPedido() {
        Payment payment = new Payment();
        payment.setType(PaymentType.PIX);
        payment.setPrice(new BigDecimal("100.00"));

        Order order = new Order();
        order.setCpfClient("12345678901");
        order.setIdProduct(1L);
        order.setPayment(payment);
        order.setStatus(OrderStatus.CREATED);

        return orderRepository.save(order);
    }
}
