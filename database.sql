-- ============================================================
-- PERSONAL FINANCE PORTAL - DATABASE SCHEMA
-- ============================================================
-- Clean, optimized schema with full support for:
-- - Users & Settings
-- - Accounts & Balances
-- - Expanded Categories with color & icon metadata
-- - Transactions with Payment Methods
-- - Monthly Budgets (month_year VARCHAR(7))
-- - Savings Goals (saved_amount DECIMAL(15,2))
-- - Recurring Bills & Subscriptions
-- - Investment Portfolio Tracking
-- - Net Worth Assets & Liabilities
-- ============================================================

DROP DATABASE IF EXISTS finance_portal;
CREATE DATABASE finance_portal CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE finance_portal;

-- ============================================================
-- 1. USERS
-- ============================================================
CREATE TABLE Users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    default_currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    theme VARCHAR(20) NOT NULL DEFAULT 'Light',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================
-- 2. ACCOUNTS
-- ============================================================
CREATE TABLE Accounts (
    account_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_accounts_user (user_id),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 3. CATEGORIES
-- System categories with color & icon metadata
-- ============================================================
CREATE TABLE Categories (
    category_id INT PRIMARY KEY AUTO_INCREMENT,
    category_name VARCHAR(100) NOT NULL,
    category_type ENUM('Income','Expense') NOT NULL,
    color VARCHAR(20) DEFAULT '#0f766e',
    icon VARCHAR(50) DEFAULT '◈',
    UNIQUE KEY uq_category (category_name, category_type)
) ENGINE=InnoDB;

INSERT INTO Categories (category_name, category_type, color, icon) VALUES
-- Expense Categories
('Housing', 'Expense', '#3b82f6', '🏠'),
('Food', 'Expense', '#f97316', '🍔'),
('Transportation', 'Expense', '#06b6d4', '🚗'),
('Lifestyle', 'Expense', '#ec4899', '🛍️'),
('Health', 'Expense', '#ef4444', '🩺'),
('Finance', 'Expense', '#64748b', '💳'),
('Bills', 'Expense', '#eab308', '⚡'),
('Entertainment', 'Expense', '#8b5cf6', '🎬'),
('Education', 'Expense', '#14b8a6', '📚'),
('Travel', 'Expense', '#10b981', '✈️'),
('Other', 'Expense', '#94a3b8', '📦'),

-- Income Categories
('Salary', 'Income', '#059669', '💼'),
('Freelance', 'Income', '#0d9488', '💻'),
('Business', 'Income', '#0284c7', '🏢'),
('Investment', 'Income', '#6366f1', '📈'),
('Bonus', 'Income', '#d97706', '🎁'),
('Other', 'Income', '#16a34a', '💵');

-- ============================================================
-- 4. TRANSACTIONS
-- source: 'manual' | 'import'
-- ============================================================
CREATE TABLE Transactions (
    transaction_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    account_id INT NOT NULL,
    category_id INT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    transaction_type ENUM('Income','Expense') NOT NULL,
    payment_method VARCHAR(50) NOT NULL DEFAULT 'Cash',
    description VARCHAR(255),
    transaction_date DATE NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'manual',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transactions_user (user_id),
    INDEX idx_transactions_account (account_id),
    INDEX idx_transactions_category (category_id),
    INDEX idx_transactions_date (transaction_date),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES Accounts(account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES Categories(category_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 5. BUDGETS
-- Monthly budgets per category using amount & month_year (YYYY-MM)
-- ============================================================
CREATE TABLE Budgets (
    budget_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    category_id INT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    month_year VARCHAR(7) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_budgets_user (user_id),
    INDEX idx_budgets_month (month_year),
    UNIQUE KEY uq_user_cat_month (user_id, category_id, month_year),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_budgets_category FOREIGN KEY (category_id) REFERENCES Categories(category_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 6. SAVINGS GOALS
-- Using saved_amount to align with application queries
-- ============================================================
CREATE TABLE SavingsGoals (
    goal_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    goal_name VARCHAR(100) NOT NULL,
    goal_type VARCHAR(50) NOT NULL DEFAULT 'savings',
    target_amount DECIMAL(15,2) NOT NULL,
    saved_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    target_date DATE NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_goals_user (user_id),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 7. BILLS & SUBSCRIPTIONS
-- ============================================================
CREATE TABLE Bills (
    bill_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    bill_name VARCHAR(100) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    due_date DATE NOT NULL,
    frequency VARCHAR(30) NOT NULL DEFAULT 'Monthly',
    bill_type VARCHAR(50) NOT NULL DEFAULT 'Bill',
    status VARCHAR(30) NOT NULL DEFAULT 'Pending',
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_bills_user (user_id),
    INDEX idx_bills_due (due_date),
    CONSTRAINT fk_bills_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 8. INVESTMENTS
-- ============================================================
CREATE TABLE Investments (
    investment_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    investment_name VARCHAR(100) NOT NULL,
    investment_type VARCHAR(50) NOT NULL,
    invested_amount DECIMAL(15,2) NOT NULL,
    current_value DECIMAL(15,2) NOT NULL,
    investment_date DATE NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_investments_user (user_id),
    CONSTRAINT fk_investments_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 9. OTHER ASSETS (for Net Worth calculation)
-- ============================================================
CREATE TABLE Assets (
    asset_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    asset_name VARCHAR(100) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_assets_user (user_id),
    CONSTRAINT fk_assets_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 10. LIABILITIES (for Net Worth calculation)
-- ============================================================
CREATE TABLE Liabilities (
    liability_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    liability_name VARCHAR(100) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_liabilities_user (user_id),
    CONSTRAINT fk_liabilities_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 11. SPENDING GOALS
-- ============================================================
CREATE TABLE SpendingGoals (
    goal_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    target_month VARCHAR(7) NOT NULL,
    target_amount DECIMAL(15,2) NOT NULL,
    based_on_month VARCHAR(7) NULL,
    based_on_amount DECIMAL(15,2) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_spending_goal_user_month (user_id, target_month),
    INDEX idx_spending_goals_user (user_id),
    CONSTRAINT fk_spending_goals_user FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE SpendingGoalCategories (
    goal_id INT NOT NULL,
    category_id INT NOT NULL,
    target_amount DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (goal_id, category_id),
    INDEX idx_spending_goal_categories_category (category_id),
    CONSTRAINT fk_spending_goal_categories_goal FOREIGN KEY (goal_id) REFERENCES SpendingGoals(goal_id) ON DELETE CASCADE,
    CONSTRAINT fk_spending_goal_categories_category FOREIGN KEY (category_id) REFERENCES Categories(category_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- DEFAULT INITIAL USER (Ready for immediate use)
-- ============================================================
INSERT INTO Users (full_name, email, default_currency, theme)
VALUES ('Demo User', 'demo@financeportal.local', 'INR', 'Light');
