package com.personalfinance.portal.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionService {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;

    public TransactionService(JdbcTemplate db, TransactionTemplate transactions) {
        this.db = db;
        this.transactions = transactions;
    }

    public long create(int userId, int accountId, int categoryId, BigDecimal amount, String type,
            String paymentMethod, String description, String date, String source) {
        if (count("SELECT COUNT(*) FROM Accounts WHERE account_id=? AND user_id=?", accountId, userId) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected account does not exist.");
        List<Map<String, Object>> categories = db.queryForList("SELECT category_type FROM Categories WHERE category_id=?", categoryId);
        if (categories.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected category does not exist.");
        if (!type.equals(categories.getFirst().get("category_type")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose an " + type + " category.");
        Long id = transactions.execute(status -> {
            KeyHolder key = new GeneratedKeyHolder();
            db.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO Transactions (user_id,account_id,category_id,amount,transaction_type,payment_method,
                        description,transaction_date,source) VALUES (?,?,?,?,?,?,?,?,?)
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setObject(1, userId);
                statement.setObject(2, accountId);
                statement.setObject(3, categoryId);
                statement.setObject(4, amount);
                statement.setObject(5, type);
                statement.setObject(6, paymentMethod);
                statement.setObject(7, description);
                statement.setObject(8, date);
                statement.setObject(9, source);
                return statement;
            }, key);
            db.update("UPDATE Accounts SET balance=balance+? WHERE account_id=? AND user_id=?",
                    type.equals("Income") ? amount : amount.negate(), accountId, userId);
            Number generatedId = key.getKey();
            return generatedId == null ? 0L : generatedId.longValue();
        });
        return id == null ? 0 : id;
    }

    public void update(int userId, int id, int accountId, int categoryId, BigDecimal amount,
            String type, String paymentMethod, String description, String date) {
        if (count("SELECT COUNT(*) FROM Accounts WHERE account_id=? AND user_id=?", accountId, userId) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected account does not exist.");
        List<Map<String, Object>> categoryRows = db.queryForList("SELECT category_type FROM Categories WHERE category_id=?", categoryId);
        if (categoryRows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected category does not exist.");
        if (!type.equals(categoryRows.getFirst().get("category_type")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose an " + type + " category.");
        List<Map<String, Object>> oldRows = db.queryForList(
                "SELECT account_id,amount,transaction_type FROM Transactions WHERE transaction_id=? AND user_id=?", id, userId);
        if (oldRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found.");
        Map<String, Object> old = oldRows.getFirst();
        transactions.executeWithoutResult(status -> {
            BigDecimal oldAmount = new BigDecimal(old.get("amount").toString());
            BigDecimal revert = "Income".equals(old.get("transaction_type")) ? oldAmount.negate() : oldAmount;
            db.update("UPDATE Accounts SET balance=balance+? WHERE account_id=? AND user_id=?", revert, old.get("account_id"), userId);
            db.update("UPDATE Accounts SET balance=balance+? WHERE account_id=? AND user_id=?",
                    type.equals("Income") ? amount : amount.negate(), accountId, userId);
            db.update("""
                    UPDATE Transactions SET account_id=?,category_id=?,amount=?,transaction_type=?,payment_method=?,
                    description=?,transaction_date=? WHERE transaction_id=? AND user_id=?
                    """, accountId, categoryId, amount, type, paymentMethod, description, date, id, userId);
        });
    }

    public void delete(int userId, int id) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT account_id,amount,transaction_type FROM Transactions WHERE transaction_id=? AND user_id=?", id, userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found.");
        Map<String, Object> tx = rows.getFirst();
        transactions.executeWithoutResult(status -> {
            BigDecimal amount = new BigDecimal(tx.get("amount").toString());
            db.update("UPDATE Accounts SET balance=balance+? WHERE account_id=? AND user_id=?",
                    "Income".equals(tx.get("transaction_type")) ? amount.negate() : amount, tx.get("account_id"), userId);
            db.update("DELETE FROM Transactions WHERE transaction_id=? AND user_id=?", id, userId);
        });
    }

    private long count(String sql, Object... args) {
        Long result = db.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }
}
