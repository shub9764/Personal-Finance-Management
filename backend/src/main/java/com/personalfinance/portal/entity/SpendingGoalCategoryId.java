package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SpendingGoalCategoryId implements Serializable {
    @Column(name = "goal_id")
    private Integer goalId;
    @Column(name = "category_id")
    private Integer categoryId;

    protected SpendingGoalCategoryId() {}

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SpendingGoalCategoryId that)) return false;
        return Objects.equals(goalId, that.goalId) && Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goalId, categoryId);
    }
}
