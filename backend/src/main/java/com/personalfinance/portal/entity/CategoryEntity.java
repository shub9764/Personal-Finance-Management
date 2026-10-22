package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Categories")
public class CategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer id;
    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;
    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false)
    private TransactionType categoryType;
    @Column(length = 20)
    private String color;
    @Column(length = 50)
    private String icon;
    protected CategoryEntity() {}
    public enum TransactionType { Income, Expense }
}
