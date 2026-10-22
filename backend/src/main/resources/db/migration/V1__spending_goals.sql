CREATE TABLE IF NOT EXISTS SpendingGoals (
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

CREATE TABLE IF NOT EXISTS SpendingGoalCategories (
    goal_id INT NOT NULL,
    category_id INT NOT NULL,
    target_amount DECIMAL(15,2) NOT NULL,
    PRIMARY KEY (goal_id, category_id),
    INDEX idx_spending_goal_categories_category (category_id),
    CONSTRAINT fk_spending_goal_categories_goal FOREIGN KEY (goal_id) REFERENCES SpendingGoals(goal_id) ON DELETE CASCADE,
    CONSTRAINT fk_spending_goal_categories_category FOREIGN KEY (category_id) REFERENCES Categories(category_id) ON DELETE CASCADE
) ENGINE=InnoDB;
