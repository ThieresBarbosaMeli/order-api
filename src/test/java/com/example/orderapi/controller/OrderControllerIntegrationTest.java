package com.example.orderapi.controller;

import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderItem;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.domain.Payment;
import com.example.orderapi.domain.PaymentType;
import com.example.orderapi.repository.IdempotencyRepository;
import com.example.orderapi.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de integração com banco H2 em memória (perfil "test").
 *
 * @WithMockUser: como a SecurityConfig protege todos os endpoints com JWT,
 * e os testes de integração não devem depender de geração de token real,
 * @WithMockUser instrui o Spring Security Test a pré-popular o SecurityContext
 * com um usuário autenticado antes de cada request — sem passar pelo JwtFilter.
 * Isso isola o comportamento de negócio dos detalhes de autenticação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        orderRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // POST /orders
    // -------------------------------------------------------------------------

    @Test
    void postOrders_deveCriarPedidoERetornar201() throws Exception {
        String body = """
                {
                  "cpf_client": "11144477735",
                  "payment": { "type": "PIX", "price": 100.00 },
                  "items": [
                    { "idProduct": 1, "quantity": 2, "price": 50.00 }
                  ]
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.cpf_client").value("11144477735"))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void postOrders_deveRetornar400SeCpfInvalido() throws Exception {
        String body = """
                {
                  "cpf_client": "123",
                  "payment": { "type": "PIX", "price": 100.00 },
                  "items": [
                    { "idProduct": 1, "quantity": 1, "price": 100.00 }
                  ]
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
                  "cpf_client": "11144477735",
                  "items": [
                    { "idProduct": 1, "quantity": 1, "price": 100.00 }
                  ]
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postOrders_deveRetornar400SeTipoPagamentoInvalido() throws Exception {
        String body = """
                {
                  "cpf_client": "11144477735",
                  "payment": { "type": "BITCOIN", "price": 100.00 },
                  "items": [
                    { "idProduct": 1, "quantity": 1, "price": 100.00 }
                  ]
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postOrders_deveRetornar400SeListaDeItensVazia() throws Exception {
        String body = """
                {
                  "cpf_client": "11144477735",
                  "payment": { "type": "PIX", "price": 100.00 },
                  "items": []
                }
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /orders/{id}/pay
    // -------------------------------------------------------------------------

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

        // Primeira chamada
        mockMvc.perform(post("/orders/{id}/pay", order.getId())
                        .header("Idempotency-Key", "chave-unica-002"))
                .andExpect(status().isOk());

        // Segunda chamada com mesma chave → deve retornar 200 sem reprocessar
        mockMvc.perform(post("/orders/{id}/pay", order.getId())
                        .header("Idempotency-Key", "chave-unica-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void postOrdersPay_deveRetornar404SePedidoNaoExiste() throws Exception {
        mockMvc.perform(post("/orders/{id}/pay", 999L)
                        .header("Idempotency-Key", "chave-404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postOrdersPay_deveRetornar409ComChaveUsadaParaOutroPedido() throws Exception {
        Order order1 = criarPedido();
        Order order2 = criarPedido();

        // Paga order1 com a chave
        mockMvc.perform(post("/orders/{id}/pay", order1.getId())
                        .header("Idempotency-Key", "chave-conflito-001"))
                .andExpect(status().isOk());

        // Tenta pagar order2 com a MESMA chave → deve retornar 409
        mockMvc.perform(post("/orders/{id}/pay", order2.getId())
                        .header("Idempotency-Key", "chave-conflito-001"))
                .andExpect(status().isConflict());
    }

    @Test
    void postOrdersPay_deveRetornar409SePedidoJaPago() throws Exception {
        Order order = criarPedido();
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        mockMvc.perform(post("/orders/{id}/pay", order.getId())
                        .header("Idempotency-Key", "chave-ja-pago"))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // PATCH /orders/{id}/status
    // -------------------------------------------------------------------------

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
        Order order = criarPedido(); // status CREATED

        String body = """
                { "status": "DELIVERED" }
                """;

        // CREATED → DELIVERED pula etapas → 409
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
    void patchOrdersStatus_deveRetornar400ComStatusInexistente() throws Exception {
        Order order = criarPedido();

        String body = """
                { "status": "CANCELADO" }
                """;

        mockMvc.perform(patch("/orders/{id}/status", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /orders/{id}
    // -------------------------------------------------------------------------

    @Test
    void getOrdersById_deveRetornarPedidoComSucesso() throws Exception {
        Order order = criarPedido();

        mockMvc.perform(get("/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.cpf_client").value("11144477735"))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void getOrdersById_deveRetornar404SePedidoNaoExiste() throws Exception {
        mockMvc.perform(get("/orders/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /orders
    // -------------------------------------------------------------------------

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
    void getOrders_deveFiltrarPorStatus() throws Exception {
        criarPedido();

        mockMvc.perform(get("/orders?status=CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CREATED"));
    }

    @Test
    void getOrders_deveOrdenarPorCampo() throws Exception {
        criarPedido();
        criarPedido();

        mockMvc.perform(get("/orders?sortBy=id&direction=desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Order criarPedido() {
        Payment payment = new Payment();
        payment.setType(PaymentType.PIX);
        payment.setPrice(new BigDecimal("100.00"));

        Order order = new Order();
        order.setCpfClient("11144477735");
        order.setPayment(payment);
        order.setStatus(OrderStatus.CREATED);

        OrderItem item = new OrderItem();
        item.setIdProduct(1L);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("100.00"));
        item.setOrder(order);
        order.getItems().add(item);

        return orderRepository.save(order);
    }
}
