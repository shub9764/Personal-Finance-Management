package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "SpendingGoals")
public class SpendingGoalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_id")
    private Integer id;
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    @Column(name = "target_month", nullable = false, length = 7)
    private String targetMonth;
    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;
    @Column(name = "based_on_month", length = 7)
    private String basedOnMonth;
    @Column(name = "based_on_amount", precision = 15, scale = 2)
    private BigDecimal basedOnAmount;
    protected SpendingGoalEntity() {}
}
