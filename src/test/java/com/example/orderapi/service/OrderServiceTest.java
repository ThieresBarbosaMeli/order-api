package com.example.orderapi.service;

import com.example.orderapi.domain.IdempotencyRecord;
import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderItem;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.domain.Payment;
import com.example.orderapi.domain.PaymentType;
import com.example.orderapi.dto.OrderItemDTO;
import com.example.orderapi.dto.OrderRequestDTO;
import com.example.orderapi.dto.OrderStatusUpdateDTO;
import com.example.orderapi.dto.PaymentDTO;
import com.example.orderapi.exception.IdempotencyConflictException;
import com.example.orderapi.exception.InvalidStatusTransitionException;
import com.example.orderapi.exception.OrderNotFoundException;
import com.example.orderapi.repository.IdempotencyRepository;
import com.example.orderapi.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @InjectMocks
    private OrderService service;

    private Order order;
    private OrderRequestDTO requestDTO;

    @BeforeEach
    void setUp() {
        Payment payment = new Payment();
        payment.setType(PaymentType.PIX);
        payment.setPrice(new BigDecimal("100.00"));

        OrderItem item = new OrderItem();
        item.setIdProduct(1L);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("100.00"));

        order = new Order();
        order.setStatus(OrderStatus.CREATED);
        order.setCpfClient("12345678901");
        order.setPayment(payment);
        item.setOrder(order);
        order.getItems().add(item);

        requestDTO = new OrderRequestDTO(
                "12345678901",
                new PaymentDTO("PIX", new BigDecimal("100.00")),
                List.of(new OrderItemDTO(1L, 1, new BigDecimal("100.00")))
        );
    }

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Test
    void deveCriarPedidoComSucesso() {
        when(repository.save(any(Order.class))).thenReturn(order);

        Order result = service.create(requestDTO);

        assertNotNull(result);
        assertEquals(OrderStatus.CREATED, result.getStatus());
        verify(repository, times(1)).save(any(Order.class));
    }

    @Test
    void deveLancarExcecaoParaTipoPagamentoInvalido() {
        OrderRequestDTO dtoInvalido = new OrderRequestDTO(
                "12345678901",
                new PaymentDTO("BITCOIN", new BigDecimal("100.00")),
                List.of(new OrderItemDTO(1L, 1, new BigDecimal("100.00")))
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(dtoInvalido));
    }

    // -------------------------------------------------------------------------
    // pay()
    // CORREÇÃO: mockado findByIdWithLock (que o serviço chama) e não findById
    // -------------------------------------------------------------------------

    @Test
    void devePagarPedidoComSucesso() {
        when(repository.findByIdWithLock(1L)).thenReturn(Optional.of(order));
        when(idempotencyRepository.findByIdempotencyKey("chave-001")).thenReturn(Optional.empty());
        when(repository.save(any(Order.class))).thenReturn(order);
        when(idempotencyRepository.save(any(IdempotencyRecord.class))).thenReturn(new IdempotencyRecord());

        Order result = service.pay(1L, "chave-001");

        assertNotNull(result);
        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(idempotencyRepository, times(1)).save(any(IdempotencyRecord.class));
    }

    @Test
    void deveLancarExcecaoAoPagarPedidoJaPago() {
        order.setStatus(OrderStatus.PAID);

        when(repository.findByIdWithLock(1L)).thenReturn(Optional.of(order));
        when(idempotencyRepository.findByIdempotencyKey("chave-001")).thenReturn(Optional.empty());

        assertThrows(InvalidStatusTransitionException.class, () -> service.pay(1L, "chave-001"));
    }

    @Test
    void deveRetornarPedidoExistenteComChaveIdempotente() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey("chave-001");
        record.setOrderId(1L);

        // Mesma chave, mesmo orderId → idempotente, retorna pedido sem novo save
        when(repository.findByIdWithLock(1L)).thenReturn(Optional.of(order));
        when(idempotencyRepository.findByIdempotencyKey("chave-001"))
                .thenReturn(Optional.of(record));

        Order result = service.pay(1L, "chave-001");

        assertNotNull(result);
        verify(repository, never()).save(any(Order.class));
        verify(idempotencyRepository, never()).save(any(IdempotencyRecord.class));
    }

    @Test
    void deveLancarConflitoPorChaveIdempotenteDePedidoDiferente() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey("chave-reutilizada");
        record.setOrderId(99L); // pertence a outro pedido

        when(repository.findByIdWithLock(1L)).thenReturn(Optional.of(order));
        when(idempotencyRepository.findByIdempotencyKey("chave-reutilizada"))
                .thenReturn(Optional.of(record));

        assertThrows(IdempotencyConflictException.class,
                () -> service.pay(1L, "chave-reutilizada"));
    }

    @Test
    void deveLancarExcecaoQuandoPedidoNaoEncontradoNoPagamento() {
        when(repository.findByIdWithLock(99L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> service.pay(99L, "chave-001"));
    }

    // -------------------------------------------------------------------------
    // updateStatus()
    // -------------------------------------------------------------------------

    @Test
    void deveAtualizarStatusComSucesso() {
        when(repository.findById(1L)).thenReturn(Optional.of(order));
        when(repository.save(any(Order.class))).thenReturn(order);

        Order result = service.updateStatus(1L, new OrderStatusUpdateDTO("PAID"));

        assertNotNull(result);
        verify(repository, times(1)).save(any(Order.class));
    }

    @Test
    void deveLancarExcecaoParaTransicaoInvalida() {
        when(repository.findById(1L)).thenReturn(Optional.of(order));

        // CREATED → DELIVERED pula PAID e SENT
        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateStatus(1L, new OrderStatusUpdateDTO("DELIVERED")));
    }

    @Test
    void deveLancarExcecaoParaStatusInvalido() {
        when(repository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStatus(1L, new OrderStatusUpdateDTO("CANCELADO")));
    }

    @Test
    void deveLancarExcecaoQuandoPedidoNaoEncontradoNoUpdateStatus() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class,
                () -> service.updateStatus(99L, new OrderStatusUpdateDTO("PAID")));
    }

    // -------------------------------------------------------------------------
    // getById()
    // -------------------------------------------------------------------------

    @Test
    void deveBuscarPedidoPorIdComSucesso() {
        when(repository.findById(1L)).thenReturn(Optional.of(order));

        Order result = service.getById(1L);

        assertNotNull(result);
        assertEquals("12345678901", result.getCpfClient());
    }

    @Test
    void deveLancarExcecaoQuandoPedidoNaoEncontrado() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> service.getById(99L));
    }
}
