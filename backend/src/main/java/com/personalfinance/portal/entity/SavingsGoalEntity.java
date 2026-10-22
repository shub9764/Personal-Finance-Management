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
@Table(name = "SavingsGoals")
public class SavingsGoalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_id")
    private Integer id;
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    @Column(name = "goal_name", nullable = false, length = 100)
    private String goalName;
    @Column(name = "goal_type", nullable = false, length = 50)
    private String goalType;
    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;
    @Column(name = "saved_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal savedAmount;
    @Column(name = "target_date")
    private LocalDate targetDate;
    protected SavingsGoalEntity() {}
}
