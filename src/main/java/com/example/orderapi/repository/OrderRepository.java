package com.example.orderapi.repository;

import com.example.orderapi.domain.Order;
import com.example.orderapi.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Carrega Order com seus items em uma única query (resolve N+1).
     * O @EntityGraph instrui o JPA a realizar um LEFT JOIN FETCH em "items".
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findById(Long id);

    /**
     * Lock pessimista para garantir atomicidade no pagamento.
     * @EntityGraph para carregar items evitando LazyInitializationException
     * com spring.jpa.open-in-view=false.
     */
    @EntityGraph(attributePaths = {"items"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    /**
     * Listagem paginada com items carregados. Sem @EntityGraph aqui
     * Hibernate geraria N queries adicionais para cada item da página.
     */
    @EntityGraph(attributePaths = {"items"})
    Page<Order> findAll(Pageable pageable);

    /**
     * Filtro por status com items eager-loaded para evitar N+1.
     */
    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
