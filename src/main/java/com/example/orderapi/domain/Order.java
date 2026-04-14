package com.example.orderapi.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 11)
    private String cpfClient;

    @Embedded
    private Payment payment;

    @Column(nullable = false)
    private Long idProduct;

    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dateBuild;

    @Column(nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dateUpdated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @PrePersist
    public void prePersist() {
        this.dateBuild = LocalDateTime.now();
        this.dateUpdated = LocalDateTime.now();
        this.status = OrderStatus.CREATED;
    }

    @PreUpdate
    public void preUpdate() {
        this.dateUpdated = LocalDateTime.now();
    }
}