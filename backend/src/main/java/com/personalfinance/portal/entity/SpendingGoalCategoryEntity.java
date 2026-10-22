package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "SpendingGoalCategories")
public class SpendingGoalCategoryEntity {
    @EmbeddedId
    private SpendingGoalCategoryId id;
    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;
    protected SpendingGoalCategoryEntity() {}
}
