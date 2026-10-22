package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Transactions")
public class TransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Integer id;
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    @Column(name = "account_id", nullable = false)
    private Integer accountId;
    @Column(name = "category_id", nullable = false)
    private Integer categoryId;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private CategoryEntity.TransactionType transactionType;
    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;
    @Column(length = 255)
    private String description;
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;
    @Column(nullable = false, length = 30)
    private String source;
    protected TransactionEntity() {}
}
