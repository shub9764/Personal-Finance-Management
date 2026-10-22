package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Investments")
public class InvestmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "investment_id")
    private Integer id;
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    @Column(name = "investment_name", nullable = false, length = 100)
    private String investmentName;
    @Column(name = "investment_type", nullable = false, length = 50)
    private String investmentType;
    @Column(name = "invested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal investedAmount;
    @Column(name = "current_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentValue;
    @Column(name = "investment_date")
    private LocalDate investmentDate;
    protected InvestmentEntity() {}
}
