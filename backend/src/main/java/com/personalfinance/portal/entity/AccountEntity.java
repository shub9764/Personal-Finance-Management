package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "Accounts")
public class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Integer id;
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;
    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;
    protected AccountEntity() {}
}
