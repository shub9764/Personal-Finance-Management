package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "Liabilities")
public class LiabilityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "liability_id")
    private Integer id;
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    @Column(name = "liability_name", nullable = false, length = 100)
    private String liabilityName;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    protected LiabilityEntity() {}
}
