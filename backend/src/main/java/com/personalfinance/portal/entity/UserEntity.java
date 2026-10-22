package com.personalfinance.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "default_currency", nullable = false, length = 10)
    private String defaultCurrency;

    @Column(nullable = false, length = 20)
    private String theme;

    protected UserEntity() {}

    public UserEntity(String fullName, String email, String defaultCurrency, String theme) {
        this.fullName = fullName;
        this.email = email;
        this.defaultCurrency = defaultCurrency;
        this.theme = theme;
    }

    public Integer getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public String getTheme() { return theme; }

    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email) { this.email = email; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
    public void setTheme(String theme) { this.theme = theme; }
}
