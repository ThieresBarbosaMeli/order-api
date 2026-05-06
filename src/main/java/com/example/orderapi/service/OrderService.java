package com.example.orderapi.service;

import com.example.orderapi.domain.IdempotencyRecord;
import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderItem;
import com.example.orderapi.domain.OrderStatus;
import com.example.orderapi.domain.Payment;
import com.example.orderapi.domain.PaymentType;
import com.example.orderapi.dto.OrderRequestDTO;
import com.example.orderapi.dto.OrderStatusUpdateDTO;
import com.example.orderapi.exception.IdempotencyConflictException;
import com.example.orderapi.exception.InvalidStatusTransitionException;
import com.example.orderapi.exception.OrderNotFoundException;
import com.example.orderapi.repository.IdempotencyRepository;
import com.example.orderapi.repository.OrderRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final IdempotencyRepository idempotencyRepository;

    public OrderService(OrderRepository repository, IdempotencyRepository idempotencyRepository) {
        this.repository = repository;
        this.idempotencyRepository = idempotencyRepository;
    }

    /**
     * Cria um pedido com seus itens de forma atômica.
     * @Transactional garante que Order + OrderItems são salvos juntos ou nenhum é salvo.
     */
    @Transactional
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

    /**
     * Processa o pagamento de um pedido com idempotência atômica.
     *
     * Correções aplicadas:
     * 1. Idempotency-Key pertencente a OUTRO pedido → 409 (antes retornava o pedido atual sem validar).
     * 2. Lock pessimista (findByIdWithLock) protege contra pagamento duplo concorrente.
     * 3. @CacheEvict garante que o cache não serve dados desatualizados após o pagamento.
     */
    @Transactional
    @CacheEvict(value = "orders", key = "#id")
    public Order pay(Long id, String idempotencyKey) {
        Order order = repository.findByIdWithLock(id)
                .orElseThrow(() -> new OrderNotFoundException("Order não encontrada com id: " + id));

        // Uma única query para checagem de idempotência (evita dupla consulta ao banco)
        var existingRecord = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRecord.isPresent()) {
            if (!existingRecord.get().getOrderId().equals(id)) {
                // Mesma chave usada para um pedido diferente → conflito real
                throw new IdempotencyConflictException(
                        "Idempotency-Key já utilizada para o pedido " + existingRecord.get().getOrderId()
                );
            }
            // Mesma chave, mesmo pedido → operação idempotente, retorna estado atual sem reprocessar
            return order;
        }

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

    /**
     * Atualiza o status do pedido seguindo o fluxo CREATED→PAID→SENT→DELIVERED.
     *
     * @Transactional adicionado: sem ele, o @CacheEvict ocorre antes do commit.
     * Se o save() falhar, o cache estaria eviccionado mas o banco desatualizado.
     */
    @Transactional
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
                    "Transição inválida: " + order.getStatus() + " → " + newStatus
            );
        }

        order.setStatus(newStatus);
        return repository.save(order);
    }

    /**
     * Busca por ID com cache.
     * @EntityGraph no repositório garante que items sejam carregados dentro da
     * transação do findById, evitando LazyInitializationException com OSIV=false.
     */
    @Cacheable(value = "orders", key = "#id")
    @Transactional(readOnly = true)
    public Order getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order não encontrada com id: " + id));
    }

    /**
     * Listagem paginada com filtro opcional por status.
     * @EntityGraph no repositório resolve o N+1 de items por pedido.
     */
    @Transactional(readOnly = true)
    public Page<Order> listAll(OrderStatus status, Pageable pageable) {
        if (status != null) {
            return repository.findByStatus(status, pageable);
        }
        return repository.findAll(pageable);
    }
}
