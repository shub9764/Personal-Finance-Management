package com.personalfinance.portal.controller;

import com.personalfinance.portal.repository.UserRepository;
import com.personalfinance.portal.entity.UserEntity;
import com.personalfinance.portal.service.TransactionService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FinanceController {
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final UserRepository users;
    private final TransactionService transactionService;

    public FinanceController(JdbcTemplate db, TransactionTemplate transactions, UserRepository users, TransactionService transactionService) {
        this.db = db;
        this.transactions = transactions;
        this.users = users;
        this.transactionService = transactionService;
    }

    private int userId(Integer queryUser, Map<String, Object> body) {
        Object value = queryUser != null ? queryUser : body.get("user_id");
        if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid user_id is required.");
        try {
            int id = Integer.parseInt(value.toString());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid user_id is required.");
        }
    }

    private static String text(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        return value == null ? fallback : value.toString().trim();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please provide a valid amount.");
        }
    }

    private static int positiveId(Object value) {
        try {
            int id = Integer.parseInt(Objects.toString(value, ""));
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please provide valid IDs.");
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static Map<String, Object> result(String message) {
        return Map.of("message", message);
    }

    private static Map<String, Object> with(Map<String, ?> values, String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>(values);
        map.put(key, value);
        return map;
    }

    private long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        Number id = key.getKey();
        return id == null ? 0 : id.longValue();
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        String database = db.queryForObject("SELECT DATABASE()", String.class);
        long userCount = count("SELECT COUNT(*) FROM Users");
        long accountCount = count("SELECT COUNT(*) FROM Accounts");
        long transactionCount = count("SELECT COUNT(*) FROM Transactions");
        return Map.of("ok", true, "database", Objects.toString(database, ""), "users", userCount,
                "accounts", accountCount, "transactions", transactionCount);
    }

    private long count(String sql, Object... args) {
        Long value = db.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    @GetMapping("/api/users")
    public List<Map<String, Object>> listUsers() {
        return db.queryForList("SELECT user_id, full_name, email, default_currency, theme FROM Users ORDER BY user_id");
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        String name = text(body, "full_name", "");
        String email = text(body, "email", "");
        String currency = text(body, "default_currency", "INR");
        String theme = text(body, "theme", "Light");
        require(!name.isBlank() && !email.isBlank(), "Please enter both full name and email.");
        try {
            UserEntity saved = users.save(new UserEntity(name, email, currency, theme));
            return userMap(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This email is already registered.");
        }
    }

    @GetMapping("/api/user/{id}")
    public Map<String, Object> getUser(@PathVariable int id) {
        return users.findById(id).map(this::userMap)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    @PutMapping("/api/user/{id}")
    public Map<String, Object> updateUser(@PathVariable int id, @RequestBody Map<String, Object> body) {
        String name = text(body, "full_name", "");
        String email = text(body, "email", "");
        require(!name.isBlank() && !email.isBlank(), "Full name and email are required.");
        try {
            UserEntity user = users.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
            user.setFullName(name);
            user.setEmail(email);
            user.setDefaultCurrency(text(body, "default_currency", "INR"));
            user.setTheme(text(body, "theme", "Light"));
            return with(userMap(users.save(user)), "message", "Settings saved successfully.");
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This email is already registered to another user.");
        }
    }

    private Map<String, Object> userMap(UserEntity entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", entity.getId());
        result.put("full_name", entity.getFullName());
        result.put("email", entity.getEmail());
        result.put("default_currency", entity.getDefaultCurrency());
        result.put("theme", entity.getTheme());
        return result;
    }

    @GetMapping("/api/categories")
    public List<Map<String, Object>> categories() {
        return db.queryForList("SELECT category_id, category_name, category_type, color, icon FROM Categories ORDER BY category_type, category_name");
    }

    @GetMapping("/api/accounts")
    public List<Map<String, Object>> accounts(@RequestParam Integer user_id) {
        return db.queryForList("SELECT account_id, account_name, account_type, balance FROM Accounts WHERE user_id=? ORDER BY account_name", user_id);
    }

    @PostMapping("/api/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createAccount(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        String name = text(body, "account_name", "");
        String type = text(body, "account_type", "");
        BigDecimal balance = decimal(body.get("balance"));
        require(!name.isBlank() && !type.isBlank(), "Please enter account name, type and valid balance.");
        long id = insert("INSERT INTO Accounts (user_id,account_name,account_type,balance) VALUES (?,?,?,?)", user, name, type, balance);
        return Map.of("id", id, "message", "Account created.");
    }

    @PutMapping("/api/accounts/{id}")
    public Map<String, Object> updateAccount(@PathVariable int id, @RequestParam(required = false) Integer user_id, @RequestBody Map<String, Object> body) {
        int user = userId(user_id, body);
        String name = text(body, "account_name", "");
        String type = text(body, "account_type", "");
        BigDecimal balance = decimal(body.get("balance"));
        require(!name.isBlank() && !type.isBlank(), "Valid account name, type and balance are required.");
        db.update("UPDATE Accounts SET account_name=?,account_type=?,balance=? WHERE account_id=? AND user_id=?", name, type, balance, id, user);
        return result("Account updated.");
    }

    @DeleteMapping("/api/accounts/{id}")
    public Map<String, Object> deleteAccount(@PathVariable int id, @RequestParam Integer user_id) {
        long txCount = count("SELECT COUNT(*) FROM Transactions WHERE account_id=? AND user_id=?", id, user_id);
        if (txCount > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete: this account has " + txCount + " transaction(s).");
        db.update("DELETE FROM Accounts WHERE account_id=? AND user_id=?", id, user_id);
        return result("Account deleted.");
    }

    @GetMapping("/api/transactions")
    public List<Map<String, Object>> listTransactions(@RequestParam Integer user_id) {
        return db.queryForList("""
                SELECT t.transaction_id,t.account_id,t.category_id,t.amount,t.transaction_type,t.payment_method,
                       t.description,t.transaction_date,t.source,DATE_FORMAT(t.transaction_date,'%Y-%m-%d') AS raw_date,
                       DATE_FORMAT(t.transaction_date,'%d %b %Y') AS formatted_date,c.category_name,
                       c.color AS category_color,c.icon AS category_icon,a.account_name
                FROM Transactions t LEFT JOIN Categories c ON t.category_id=c.category_id
                LEFT JOIN Accounts a ON t.account_id=a.account_id WHERE t.user_id=?
                ORDER BY t.transaction_date DESC,t.transaction_id DESC
                """, user_id);
    }

    @PostMapping("/api/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTransaction(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        int account = positiveId(body.get("account_id"));
        int category = positiveId(body.get("category_id"));
        BigDecimal amount = decimal(body.get("amount"));
        String type = text(body, "transaction_type", "");
        String date = text(body, "transaction_date", "");
        String method = text(body, "payment_method", "Cash");
        String description = text(body, "description", "");
        String source = text(body, "source", "manual");
        require(amount.signum() > 0 && !date.isBlank() && Set.of("Income", "Expense").contains(type),
                "Please provide valid transaction details.");
        long id = transactionService.create(user, account, category, amount, type, method,
                description.isBlank() ? null : description, date, source);
        return Map.of("id", id, "message", "Transaction created.");
    }

    @PutMapping("/api/transactions/{id}")
    public Map<String, Object> updateTransaction(@PathVariable int id, @RequestParam(required = false) Integer user_id, @RequestBody Map<String, Object> body) {
        int user = userId(user_id, body);
        int account = positiveId(body.get("account_id"));
        int category = positiveId(body.get("category_id"));
        BigDecimal amount = decimal(body.get("amount"));
        String type = text(body, "transaction_type", "");
        String date = text(body, "transaction_date", "");
        String method = text(body, "payment_method", "Cash");
        String description = text(body, "description", "");
        require(amount.signum() > 0 && !date.isBlank() && Set.of("Income", "Expense").contains(type),
                "Please provide valid transaction fields.");
        transactionService.update(user, id, account, category, amount, type, method, description.isBlank() ? null : description, date);
        return result("Transaction updated.");
    }

    @DeleteMapping("/api/transactions/{id}")
    public Map<String, Object> deleteTransaction(@PathVariable int id, @RequestParam Integer user_id) {
        transactionService.delete(user_id, id);
        return result("Transaction deleted.");
    }

    @GetMapping("/api/dashboard")
    public Map<String, Object> dashboard(@RequestParam Integer user_id, @RequestParam(required = false) String month) {
        BigDecimal balance = db.queryForObject("SELECT COALESCE(SUM(balance),0) FROM Accounts WHERE user_id=?", BigDecimal.class, user_id);
        String dateFilter = validMonth(month) ? "DATE_FORMAT(transaction_date,'%Y-%m')=?" :
                "YEAR(transaction_date)=YEAR(CURDATE()) AND MONTH(transaction_date)=MONTH(CURDATE())";
        List<Object> args = validMonth(month) ? List.of(user_id, month) : List.of(user_id);
        BigDecimal income = db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM Transactions WHERE user_id=? AND transaction_type='Income' AND " + dateFilter,
                BigDecimal.class, args.toArray());
        BigDecimal expenses = db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM Transactions WHERE user_id=? AND transaction_type='Expense' AND " + dateFilter,
                BigDecimal.class, args.toArray());
        BigDecimal savings = income.subtract(expenses);
        int rate = income.signum() > 0 ? savings.multiply(BigDecimal.valueOf(100)).divide(income, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
        return Map.of("balance", zero(balance), "income", zero(income), "expenses", zero(expenses),
                "savings", savings, "savingsRate", rate);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean validMonth(String month) {
        return month != null && month.matches("\\d{4}-\\d{2}");
    }

    @GetMapping("/api/dashboard/monthly")
    public List<Map<String, Object>> monthlyDashboard(@RequestParam Integer user_id) {
        return db.queryForList("""
                SELECT DATE_FORMAT(transaction_date,'%Y-%m') AS month,DATE_FORMAT(transaction_date,'%b') AS month_label,
                 COALESCE(SUM(CASE WHEN transaction_type='Income' THEN amount ELSE 0 END),0) AS income,
                 COALESCE(SUM(CASE WHEN transaction_type='Expense' THEN amount ELSE 0 END),0) AS expenses
                FROM Transactions WHERE user_id=? AND transaction_date>=DATE_SUB(DATE_FORMAT(CURDATE(),'%Y-%m-01'),INTERVAL 5 MONTH)
                GROUP BY month,month_label ORDER BY month ASC
                """, user_id);
    }

    @GetMapping("/api/dashboard/categories")
    public List<Map<String, Object>> dashboardCategories(@RequestParam Integer user_id, @RequestParam(required = false) String month) {
        String dateFilter = validMonth(month) ? "DATE_FORMAT(t.transaction_date,'%Y-%m')=?" :
                "YEAR(t.transaction_date)=YEAR(CURDATE()) AND MONTH(t.transaction_date)=MONTH(CURDATE())";
        List<Object> args = new ArrayList<>(List.of(user_id));
        if (validMonth(month)) args.add(month);
        return db.queryForList("""
                SELECT c.category_id,c.category_name,c.color,c.icon,COALESCE(SUM(t.amount),0) AS total
                FROM Transactions t JOIN Categories c ON t.category_id=c.category_id
                WHERE t.user_id=? AND t.transaction_type='Expense' AND
                """ + dateFilter + """
                GROUP BY c.category_id,c.category_name,c.color,c.icon ORDER BY total DESC
                """, args.toArray());
    }

    @GetMapping("/api/budgets")
    public List<Map<String, Object>> budgets(@RequestParam Integer user_id, @RequestParam(required = false) String month) {
        String monthFilter = validMonth(month) ? " AND b.month_year=?" : "";
        List<Object> args = new ArrayList<>(List.of(user_id));
        if (validMonth(month)) args.add(month);
        return db.queryForList("""
                SELECT b.budget_id,b.category_id,b.amount,b.month_year,c.category_name,
                 c.color AS category_color,c.icon AS category_icon,
                 COALESCE((SELECT SUM(t.amount) FROM Transactions t WHERE t.user_id=b.user_id
                  AND t.category_id=b.category_id AND t.transaction_type='Expense'
                  AND DATE_FORMAT(t.transaction_date,'%Y-%m')=b.month_year),0) AS spent
                FROM Budgets b JOIN Categories c ON b.category_id=c.category_id WHERE b.user_id=?
                """ + monthFilter + " ORDER BY b.month_year DESC,c.category_name ASC", args.toArray());
    }

    @PostMapping("/api/budgets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createBudget(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        int category = positiveId(body.get("category_id"));
        BigDecimal amount = decimal(body.get("amount"));
        String month = text(body, "month_year", "");
        require(amount.signum() > 0 && validMonth(month), "Please enter valid budget details.");
        db.update("""
                INSERT INTO Budgets (user_id,category_id,amount,month_year) VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE amount=VALUES(amount)
                """, user, category, amount, month);
        Long id = db.queryForObject("SELECT budget_id FROM Budgets WHERE user_id=? AND category_id=? AND month_year=?",
                Long.class, user, category, month);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("message", "Budget saved.");
        return response;
    }

    @PutMapping("/api/budgets/{id}")
    public Map<String, Object> updateBudget(@PathVariable int id, @RequestParam(required = false) Integer user_id, @RequestBody Map<String, Object> body) {
        int user = userId(user_id, body);
        int category = positiveId(body.get("category_id"));
        BigDecimal amount = decimal(body.get("amount"));
        String month = text(body, "month_year", "");
        require(amount.signum() > 0 && validMonth(month), "Please provide valid budget details.");
        db.update("UPDATE Budgets SET category_id=?,amount=?,month_year=? WHERE budget_id=? AND user_id=?", category, amount, month, id, user);
        return result("Budget updated.");
    }

    @DeleteMapping("/api/budgets/{id}")
    public Map<String, Object> deleteBudget(@PathVariable int id, @RequestParam Integer user_id) {
        db.update("DELETE FROM Budgets WHERE budget_id=? AND user_id=?", id, user_id);
        return result("Budget deleted.");
    }

    @GetMapping("/api/goals")
    public List<Map<String, Object>> goals(@RequestParam Integer user_id) {
        return db.queryForList("""
                SELECT goal_id,user_id,goal_name,goal_type,target_amount,saved_amount,target_date,
                 DATE_FORMAT(target_date,'%Y-%m-%d') AS raw_target_date,
                 DATE_FORMAT(target_date,'%d %b %Y') AS formatted_target_date
                FROM SavingsGoals WHERE user_id=? ORDER BY goal_type,target_date ASC,goal_id ASC
                """, user_id);
    }

    @PostMapping("/api/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createGoal(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        String name = text(body, "goal_name", "");
        BigDecimal target = decimal(body.get("target_amount"));
        BigDecimal saved = decimal(body.get("saved_amount"));
        String date = text(body, "target_date", "");
        require(!name.isBlank() && target.signum() > 0, "Please enter a goal name and target amount.");
        long id = insert("INSERT INTO SavingsGoals (user_id,goal_name,goal_type,target_amount,saved_amount,target_date) VALUES (?,?,?,?,?,?)",
                user, name, text(body, "goal_type", "savings"), target, saved, date.isBlank() ? null : date);
        return Map.of("id", id, "message", "Goal created.");
    }

    @PutMapping("/api/goals/{id}")
    public Map<String, Object> updateGoal(@PathVariable int id, @RequestParam(required = false) Integer user_id, @RequestBody Map<String, Object> body) {
        int user = userId(user_id, body);
        String name = text(body, "goal_name", "");
        BigDecimal target = decimal(body.get("target_amount"));
        BigDecimal saved = decimal(body.get("saved_amount"));
        String date = text(body, "target_date", "");
        require(!name.isBlank() && target.signum() > 0, "Please enter a goal name and target amount.");
        db.update("UPDATE SavingsGoals SET goal_name=?,goal_type=?,target_amount=?,saved_amount=?,target_date=? WHERE goal_id=? AND user_id=?",
                name, text(body, "goal_type", "savings"), target, saved, date.isBlank() ? null : date, id, user);
        return result("Goal updated.");
    }

    @DeleteMapping("/api/goals/{id}")
    public Map<String, Object> deleteGoal(@PathVariable int id, @RequestParam Integer user_id) {
        db.update("DELETE FROM SavingsGoals WHERE goal_id=? AND user_id=?", id, user_id);
        return result("Goal deleted.");
    }

    @GetMapping("/api/bills")
    public List<Map<String, Object>> bills(@RequestParam Integer user_id) {
        return db.queryForList("""
                SELECT bill_id,user_id,bill_name,amount,due_date,DATE_FORMAT(due_date,'%Y-%m-%d') AS raw_due_date,
                 DATE_FORMAT(due_date,'%d %b %Y') AS formatted_due,frequency,bill_type,status,active
                FROM Bills WHERE user_id=? ORDER BY due_date ASC
                """, user_id);
    }

    @PostMapping("/api/bills")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createBill(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        String name = text(body, "bill_name", "");
        BigDecimal amount = decimal(body.get("amount"));
        String due = text(body, "due_date", "");
        require(!name.isBlank() && amount.signum() > 0 && !due.isBlank(), "Please enter bill name, amount, and due date.");
        long id = insert("""
                INSERT INTO Bills (user_id,bill_name,amount,due_date,frequency,bill_type,status,active)
                VALUES (?,?,?,?,?,?,?,?)
                """, user, name, amount, due, text(body, "frequency", "Monthly"), text(body, "bill_type", "Bill"),
                text(body, "status", "Pending"), active(body));
        return Map.of("id", id, "message", "Bill added.");
    }

    @PutMapping("/api/bills/{id}")
    public Map<String, Object> updateBill(@PathVariable int id, @RequestParam(required = false) Integer user_id, @RequestBody Map<String, Object> body) {
        int user = userId(user_id, body);
        db.update("""
                UPDATE Bills SET bill_name=?,amount=?,due_date=?,frequency=?,bill_type=?,status=?,active=?
                WHERE bill_id=? AND user_id=?
                """, text(body, "bill_name", ""), decimal(body.get("amount")), text(body, "due_date", ""),
                text(body, "frequency", "Monthly"), text(body, "bill_type", "Bill"), text(body, "status", "Pending"),
                active(body), id, user);
        return result("Bill updated.");
    }

    @DeleteMapping("/api/bills/{id}")
    public Map<String, Object> deleteBill(@PathVariable int id, @RequestParam Integer user_id) {
        db.update("DELETE FROM Bills WHERE bill_id=? AND user_id=?", id, user_id);
        return result("Bill deleted.");
    }

    @GetMapping("/api/investments")
    public List<Map<String, Object>> investments(@RequestParam Integer user_id) {
        return db.queryForList("""
                SELECT investment_id,user_id,investment_name,investment_type,invested_amount,current_value,investment_date,
                 DATE_FORMAT(investment_date,'%Y-%m-%d') AS raw_date,DATE_FORMAT(investment_date,'%d %b %Y') AS formatted_date
                FROM Investments WHERE user_id=? ORDER BY investment_date DESC,investment_id DESC
                """, user_id);
    }

    @PostMapping("/api/investments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createInvestment(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        String name = text(body, "investment_name", "");
        BigDecimal invested = decimal(body.get("invested_amount"));
        BigDecimal value = decimal(body.get("current_value"));
        String date = text(body, "investment_date", "");
        require(!name.isBlank(), "Please enter valid investment details.");
        long id = insert("""
                INSERT INTO Investments (user_id,investment_name,investment_type,invested_amount,current_value,investment_date)
                VALUES (?,?,?,?,?,?)
                """, user, name, text(body, "investment_type", "Stocks"), invested, value, date.isBlank() ? null : date);
        return Map.of("id", id, "message", "Investment added.");
    }

    @PutMapping("/api/investments/{id}")
    public Map<String, Object> updateInvestment(@PathVariable int id, @RequestParam(required = false) Integer user_id, @RequestBody Map<String, Object> body) {
        int user = userId(user_id, body);
        String date = text(body, "investment_date", "");
        db.update("""
                UPDATE Investments SET investment_name=?,investment_type=?,invested_amount=?,current_value=?,investment_date=?
                WHERE investment_id=? AND user_id=?
                """, text(body, "investment_name", ""), text(body, "investment_type", "Stocks"),
                decimal(body.get("invested_amount")), decimal(body.get("current_value")), date.isBlank() ? null : date, id, user);
        return result("Investment updated.");
    }

    @DeleteMapping("/api/investments/{id}")
    public Map<String, Object> deleteInvestment(@PathVariable int id, @RequestParam Integer user_id) {
        db.update("DELETE FROM Investments WHERE investment_id=? AND user_id=?", id, user_id);
        return result("Investment deleted.");
    }

    @GetMapping("/api/networth")
    public Map<String, Object> netWorth(@RequestParam Integer user_id) {
        BigDecimal bank = total("SELECT COALESCE(SUM(balance),0) FROM Accounts WHERE user_id=?", user_id);
        BigDecimal investments = total("SELECT COALESCE(SUM(current_value),0) FROM Investments WHERE user_id=?", user_id);
        BigDecimal other = total("SELECT COALESCE(SUM(amount),0) FROM Assets WHERE user_id=?", user_id);
        BigDecimal liabilities = total("SELECT COALESCE(SUM(amount),0) FROM Liabilities WHERE user_id=?", user_id);
        BigDecimal assets = bank.add(investments).add(other);
        return Map.of("bank", bank, "investments", investments, "otherAssets", other,
                "assets", assets, "liabilities", liabilities, "netWorth", assets.subtract(liabilities));
    }

    private BigDecimal total(String sql, Object... args) {
        return zero(db.queryForObject(sql, BigDecimal.class, args));
    }

    @GetMapping("/api/assets")
    public List<Map<String, Object>> assets(@RequestParam Integer user_id) {
        return db.queryForList("SELECT * FROM Assets WHERE user_id=? ORDER BY asset_id", user_id);
    }

    @PostMapping("/api/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createAsset(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        String name = text(body, "asset_name", "");
        BigDecimal amount = decimal(body.get("amount"));
        require(!name.isBlank(), "Please enter asset name and value.");
        return Map.of("id", insert("INSERT INTO Assets (user_id,asset_name,amount) VALUES (?,?,?)", user, name, amount));
    }

    @DeleteMapping("/api/assets/{id}")
    public Map<String, Object> deleteAsset(@PathVariable int id, @RequestParam Integer user_id) {
        db.update("DELETE FROM Assets WHERE asset_id=? AND user_id=?", id, user_id);
        return result("Asset deleted.");
    }

    @GetMapping("/api/liabilities")
    public List<Map<String, Object>> liabilities(@RequestParam Integer user_id) {
        return db.queryForList("SELECT * FROM Liabilities WHERE user_id=? ORDER BY liability_id", user_id);
    }

    @PostMapping("/api/liabilities")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createLiability(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        String name = text(body, "liability_name", "");
        BigDecimal amount = decimal(body.get("amount"));
        require(!name.isBlank(), "Please enter liability name and amount.");
        return Map.of("id", insert("INSERT INTO Liabilities (user_id,liability_name,amount) VALUES (?,?,?)", user, name, amount));
    }

    @DeleteMapping("/api/liabilities/{id}")
    public Map<String, Object> deleteLiability(@PathVariable int id, @RequestParam Integer user_id) {
        db.update("DELETE FROM Liabilities WHERE liability_id=? AND user_id=?", id, user_id);
        return result("Liability deleted.");
    }

    @GetMapping("/api/reports")
    public Map<String, Object> reports(@RequestParam Integer user_id) {
        List<Map<String, Object>> monthly = db.queryForList("""
                SELECT DATE_FORMAT(transaction_date,'%Y-%m') AS month,DATE_FORMAT(transaction_date,'%M %Y') AS month_name,
                 COALESCE(SUM(CASE WHEN transaction_type='Income' THEN amount ELSE 0 END),0) AS income,
                 COALESCE(SUM(CASE WHEN transaction_type='Expense' THEN amount ELSE 0 END),0) AS expenses
                FROM Transactions WHERE user_id=? GROUP BY month,month_name ORDER BY month DESC LIMIT 12
                """, user_id);
        List<Map<String, Object>> categoryRows = db.queryForList("""
                SELECT c.category_id,c.category_name,c.color,c.icon,COUNT(t.transaction_id) AS tx_count,
                 COALESCE(SUM(t.amount),0) AS total
                FROM Transactions t JOIN Categories c ON t.category_id=c.category_id
                WHERE t.user_id=? AND t.transaction_type='Expense'
                 AND YEAR(t.transaction_date)=YEAR(CURDATE()) AND MONTH(t.transaction_date)=MONTH(CURDATE())
                GROUP BY c.category_id,c.category_name,c.color,c.icon ORDER BY total DESC
                """, user_id);
        return Map.of("monthly", monthly, "categories", categoryRows);
    }

    @GetMapping("/api/assistant")
    public Map<String, Object> assistant(@RequestParam Integer user_id, @RequestParam(defaultValue = "") String q) {
        String query = q.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> userRows = db.queryForList("SELECT default_currency FROM Users WHERE user_id=?", user_id);
        String currency = userRows.isEmpty() ? "INR" : Objects.toString(userRows.getFirst().get("default_currency"), "INR");
        String answer = "I can help with your spending, savings, budgets, bills, or investment data.";
        if (containsAny(query, "most", "spend", "largest", "expense")) {
            List<Map<String, Object>> rows = db.queryForList("""
                    SELECT c.category_name,SUM(t.amount) AS total FROM Transactions t JOIN Categories c ON t.category_id=c.category_id
                    WHERE t.user_id=? AND t.transaction_type='Expense' AND YEAR(t.transaction_date)=YEAR(CURDATE())
                    AND MONTH(t.transaction_date)=MONTH(CURDATE()) GROUP BY c.category_id,c.category_name ORDER BY total DESC LIMIT 1
                    """, user_id);
            answer = rows.isEmpty() ? "No expenses recorded this month." :
                    "Your highest expense category this month is \"" + rows.getFirst().get("category_name") + "\" at " + money(rows.getFirst().get("total"), currency) + ".";
        } else if (containsAny(query, "save", "saving", "cash flow", "rate")) {
            List<Map<String, Object>> rows = db.queryForList("""
                    SELECT COALESCE(SUM(CASE WHEN transaction_type='Income' THEN amount ELSE 0 END),0) AS income,
                    COALESCE(SUM(CASE WHEN transaction_type='Expense' THEN amount ELSE 0 END),0) AS expenses
                    FROM Transactions WHERE user_id=? AND YEAR(transaction_date)=YEAR(CURDATE()) AND MONTH(transaction_date)=MONTH(CURDATE())
                    """, user_id);
            BigDecimal income = decimal(rows.getFirst().get("income"));
            BigDecimal expense = decimal(rows.getFirst().get("expenses"));
            BigDecimal net = income.subtract(expense);
            int rate = income.signum() > 0 ? net.multiply(BigDecimal.valueOf(100)).divide(income, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
            answer = "This month: Earned " + money(income, currency) + ", Spent " + money(expense, currency) + ", Net savings " + money(net, currency) + " (" + rate + "% savings rate).";
        } else if (containsAny(query, "bill", "subscription", "due", "recurring")) {
            List<Map<String, Object>> rows = db.queryForList("""
                    SELECT bill_name,amount,due_date,frequency FROM Bills WHERE user_id=? AND active=1
                    AND due_date>=CURDATE() ORDER BY due_date ASC LIMIT 5
                    """, user_id);
            answer = rows.isEmpty() ? "No upcoming bills found." : "Upcoming bills: " + rows.stream()
                    .map(row -> row.get("bill_name") + " (" + money(row.get("amount"), currency) + ", " + row.get("frequency") + ", due " + row.get("due_date") + ")")
                    .collect(java.util.stream.Collectors.joining("; "));
        } else if (containsAny(query, "budget", "limit")) {
            List<Map<String, Object>> rows = db.queryForList("""
                    SELECT c.category_name,b.amount,COALESCE((SELECT SUM(t.amount) FROM Transactions t
                    WHERE t.user_id=b.user_id AND t.category_id=b.category_id AND t.transaction_type='Expense'
                    AND DATE_FORMAT(t.transaction_date,'%Y-%m')=b.month_year),0) AS spent
                    FROM Budgets b JOIN Categories c ON b.category_id=c.category_id
                    WHERE b.user_id=? AND b.month_year=DATE_FORMAT(CURDATE(),'%Y-%m')
                    """, user_id);
            answer = rows.isEmpty() ? "No budgets for this month." : "Current budgets: " + rows.stream().map(row -> {
                BigDecimal limit = decimal(row.get("amount"));
                BigDecimal spent = decimal(row.get("spent"));
                int pct = limit.signum() == 0 ? 0 : spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, java.math.RoundingMode.HALF_UP).intValue();
                return row.get("category_name") + ": " + money(spent, currency) + " of " + money(limit, currency) + " (" + pct + "% used)";
            }).collect(java.util.stream.Collectors.joining("; "));
        } else if (containsAny(query, "net worth", "asset", "worth")) {
            BigDecimal assets = total("SELECT COALESCE(SUM(balance),0) FROM Accounts WHERE user_id=?", user_id)
                    .add(total("SELECT COALESCE(SUM(current_value),0) FROM Investments WHERE user_id=?", user_id))
                    .add(total("SELECT COALESCE(SUM(amount),0) FROM Assets WHERE user_id=?", user_id));
            BigDecimal liabilities = total("SELECT COALESCE(SUM(amount),0) FROM Liabilities WHERE user_id=?", user_id);
            answer = "Net Worth: " + money(assets.subtract(liabilities), currency) + " (Assets: " + money(assets, currency) + ", Liabilities: " + money(liabilities, currency) + ").";
        }
        return Map.of("answer", answer);
    }

    private static boolean containsAny(String value, String... options) {
        for (String option : options) if (value.contains(option)) return true;
        return false;
    }

    private static int active(Map<String, Object> body) {
        if (!body.containsKey("active")) return 1;
        Object value = body.get("active");
        if (value instanceof Boolean bool) return bool ? 1 : 0;
        if (value instanceof Number number) return number.doubleValue() == 0 ? 0 : 1;
        return value == null ? 0 : 1;
    }

    private static String money(Object value, String currency) {
        java.text.NumberFormat format = java.text.NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        try {
            format.setCurrency(java.util.Currency.getInstance(currency));
        } catch (IllegalArgumentException ignored) {
        }
        format.setMaximumFractionDigits(0);
        return format.format(decimal(value));
    }

    @PostMapping("/api/import/upload")
    public Map<String, Object> uploadStatement(@RequestParam Integer user_id, @RequestPart("statement") MultipartFile file) {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded. Please select a CSV, Excel, or PDF file.");
        String filename = Objects.toString(file.getOriginalFilename(), "statement").replaceAll("[^a-zA-Z0-9._-]", "_");
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!Set.of("csv", "xlsx", "xls", "pdf").contains(extension))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CSV, Excel (.xlsx/.xls) and PDF files are supported.");
        try {
            List<Map<String, String>> rows;
            if (extension.equals("csv")) rows = parseCsv(file.getBytes());
            else if (extension.equals("xlsx") || extension.equals("xls")) rows = parseSpreadsheet(file.getBytes());
            else rows = parsePdf(file.getBytes());
            List<Map<String, Object>> parsed = normalizeRows(rows);
            if (parsed.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No valid transactions found in this file. Please ensure it is a bank statement with Date, Description, and Amount columns.");
            return importPreview(user_id, filename, parsed);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to process the statement. Check that the file is a valid CSV, Excel, or PDF statement.");
        }
    }

    private List<Map<String, String>> parseCsv(byte[] bytes) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setTrim(true).build().parse(new java.io.InputStreamReader(new ByteArrayInputStream(bytes), java.nio.charset.StandardCharsets.UTF_8))) {
            for (org.apache.commons.csv.CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : parser.getHeaderNames()) row.put(header, record.get(header));
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseSpreadsheet(byte[] bytes) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) return rows;
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) return rows;
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < header.getLastCellNum(); c++) headers.add(formatter.formatCellValue(header.getCell(c)).trim());
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row current = sheet.getRow(r);
                if (current == null) continue;
                Map<String, String> row = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String value = formatter.formatCellValue(current.getCell(c)).trim();
                    row.put(headers.get(c), value);
                    hasValue |= !value.isBlank();
                }
                if (hasValue) rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parsePdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            List<Map<String, String>> rows = new ArrayList<>();
            Pattern datePattern = Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4})");
            Pattern amountPattern = Pattern.compile("[\\d,]+\\.?\\d{0,2}");
            for (String line : text.split("\\R")) {
                Matcher date = datePattern.matcher(line);
                if (!date.find()) continue;
                String rest = line.replace(date.group(), "").trim();
                Matcher amountMatcher = amountPattern.matcher(rest);
                List<String> amounts = new ArrayList<>();
                while (amountMatcher.find()) amounts.add(amountMatcher.group());
                if (amounts.isEmpty()) continue;
                String description = amountPattern.matcher(rest).replaceAll("").trim();
                Map<String, String> row = new LinkedHashMap<>();
                row.put("Date", date.group());
                row.put("Description", description);
                row.put("Amount", amounts.getLast() + (line.matches("(?i).*\\b(cr|credit|deposit)\\b.*") ? " CR" : " DR"));
                rows.add(row);
            }
            return rows;
        }
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, String>> rawRows) {
        if (rawRows.isEmpty()) return List.of();
        Map<String, String> headers = rawRows.getFirst();
        String dateKey = header(headers, "date", "txndate", "valuedate");
        String descriptionKey = header(headers, "desc", "narration", "particular", "details", "remarks", "transaction");
        String debitKey = header(headers, "debit", "withdrawal", "dr");
        String creditKey = header(headers, "credit", "deposit", "cr");
        String amountKey = header(headers, "amount", "amt");
        String balanceKey = header(headers, "balance", "bal");
        String refKey = header(headers, "ref", "chequeno", "txnid");
        if (dateKey == null) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, String> row : rawRows) {
            LocalDate date = parseDate(row.get(dateKey));
            if (date == null) continue;
            String description = Objects.toString(descriptionKey == null ? "" : row.get(descriptionKey), "").trim();
            if (description.length() > 255) description = description.substring(0, 255);
            BigDecimal amount;
            String type;
            if (debitKey != null && creditKey != null) {
                BigDecimal debit = cleanAmount(row.get(debitKey));
                BigDecimal credit = cleanAmount(row.get(creditKey));
                if (credit.signum() > 0) { amount = credit; type = "Income"; }
                else if (debit.signum() > 0) { amount = debit; type = "Expense"; }
                else continue;
            } else if (amountKey != null) {
                String raw = Objects.toString(row.get(amountKey), "").trim();
                amount = cleanAmount(raw);
                if (amount.signum() <= 0) continue;
                if (raw.matches("(?i).*\\bcr\\s*$")) type = "Income";
                else if (raw.matches("(?i).*\\bdr\\s*$")) type = "Expense";
                else if (raw.replaceAll("[₹$,_\\s]", "").startsWith("-")) type = "Expense";
                else type = description.toLowerCase(Locale.ROOT).matches(".*(salary|credit).*") ? "Income" : "Expense";
            } else continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("description", description);
            item.put("amount", amount);
            item.put("type", type);
            item.put("ref", refKey == null ? "" : Objects.toString(row.get(refKey), ""));
            item.put("balance", balanceKey == null ? null : cleanAmount(row.get(balanceKey)));
            item.put("suggestedCategory", autoCategory(description, type));
            item.put("paymentMethod", "Bank Transfer");
            result.add(item);
        }
        return result;
    }

    private static String header(Map<String, String> row, String... keywords) {
        for (String key : row.keySet()) {
            String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            for (String keyword : keywords) if (normalized.contains(keyword)) return key;
        }
        return null;
    }

    private static BigDecimal cleanAmount(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        String cleaned = raw.replaceAll("[₹$,_\\s]", "").replace("(", "-").replace(")", "").replaceAll("(?i)cr$|dr$", "");
        try {
            return new BigDecimal(cleaned).abs();
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"),
                DateTimeFormatter.ofPattern("d-M-uuuu"),
                DateTimeFormatter.ofPattern("M/d/uuuu"),
                DateTimeFormatter.ofPattern("d/M/uu"),
                DateTimeFormatter.ofPattern("d-M-uu"),
                DateTimeFormatter.ofPattern("M/d/uu"),
                DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM uu", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d-MMM-uu", Locale.ENGLISH))) {
            try { return LocalDate.parse(value, formatter); } catch (RuntimeException ignored) {}
        }
        return null;
    }

    private static String autoCategory(String description, String type) {
        String text = description.toLowerCase(Locale.ROOT);
        if ("Income".equals(type)) {
            if (containsAny(text, "salary", "payroll", "pay credit", "neft cr", "ctc")) return "Salary";
            if (containsAny(text, "freelance", "upwork", "fiverr", "toptal")) return "Freelance";
            if (containsAny(text, "dividend", "mutual fund", "zerodha", "groww", "interest credit", "fd interest")) return "Investment";
            if (containsAny(text, "bonus", "incentive", "reward")) return "Bonus";
            return "Other";
        }
        if (containsAny(text, "rent", "housing", "landlord", "hostel", "accommodation")) return "Housing";
        if (containsAny(text, "swiggy", "zomato", "uber eats", "foodpanda", "restaurant", "cafe", "coffee", "pizza", "food", "bakery")) return "Food";
        if (containsAny(text, "uber", "ola", "rapido", "taxi", "metro", "railway", "petrol", "fuel", "parking", "toll", "vehicle")) return "Transportation";
        if (containsAny(text, "netflix", "spotify", "movie", "cinema", "concert", "gaming", "entertainment")) return "Entertainment";
        if (containsAny(text, "amazon", "flipkart", "myntra", "shopping", "mall", "store", "clothing")) return "Lifestyle";
        if (containsAny(text, "hospital", "clinic", "doctor", "pharmacy", "medicine", "health", "gym", "insurance")) return "Health";
        if (containsAny(text, "electricity", "water bill", "gas bill", "jio", "airtel", "recharge", "broadband", "internet", "wifi")) return "Bills";
        if (containsAny(text, "emi", "loan", "credit card", "bank charge", "bank fee", "tax")) return "Finance";
        if (containsAny(text, "school", "college", "tuition", "coursera", "udemy", "book", "stationery")) return "Education";
        if (containsAny(text, "flight", "airline", "hotel", "travel", "holiday", "trip", "visa")) return "Travel";
        return "Other";
    }

    private Map<String, Object> importPreview(int user, String filename, List<Map<String, Object>> parsed) {
        List<Map<String, Object>> categories = db.queryForList("SELECT category_id,category_name,category_type FROM Categories");
        List<Map<String, Object>> accounts = db.queryForList("SELECT account_id,account_name FROM Accounts WHERE user_id=? LIMIT 1", user);
        List<Map<String, Object>> existing = db.queryForList("""
                SELECT amount,transaction_date,description FROM Transactions
                WHERE user_id=? AND transaction_date>=DATE_SUB(CURDATE(),INTERVAL 12 MONTH)
                """, user);
        Set<String> duplicates = new HashSet<>();
        for (Map<String, Object> tx : existing) duplicates.add(duplicateKey(tx.get("transaction_date"), tx.get("amount"), tx.get("description")));
        Map<String, Integer> categoryIds = new HashMap<>();
        for (Map<String, Object> category : categories)
            categoryIds.put(category.get("category_name") + "_" + category.get("category_type"), ((Number) category.get("category_id")).intValue());
        List<Map<String, Object>> preview = new ArrayList<>();
        int duplicateCount = 0;
        for (int i = 0; i < parsed.size(); i++) {
            Map<String, Object> tx = parsed.get(i);
            String type = Objects.toString(tx.get("type"));
            int categoryId = categoryIds.getOrDefault(tx.get("suggestedCategory") + "_" + type,
                    categoryIds.getOrDefault(("Expense".equals(type) ? "Other_Expense" : "Other_Income"), 0));
            boolean duplicate = duplicates.contains(duplicateKey(tx.get("date"), tx.get("amount"), tx.get("description")));
            if (duplicate) duplicateCount++;
            Map<String, Object> row = new LinkedHashMap<>(tx);
            row.put("idx", i);
            row.put("category_id", categoryId == 0 ? null : categoryId);
            row.put("isDuplicate", duplicate);
            row.put("import", !duplicate);
            preview.add(row);
        }
        Object defaultAccountId = accounts.isEmpty() ? null : accounts.getFirst().get("account_id");
        Object defaultAccountName = accounts.isEmpty() ? null : accounts.getFirst().get("account_name");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filename", filename);
        response.put("total", preview.size());
        response.put("duplicates", duplicateCount);
        response.put("categories", categories);
        response.put("accounts", accounts);
        response.put("defaultAccountId", defaultAccountId);
        response.put("defaultAccountName", defaultAccountName);
        response.put("transactions", preview);
        return response;
    }

    private static String duplicateKey(Object date, Object amount, Object description) {
        String d = Objects.toString(description, "").toLowerCase(Locale.ROOT);
        if (d.length() > 20) d = d.substring(0, 20);
        return date + "_" + decimal(amount).setScale(2, java.math.RoundingMode.HALF_UP) + "_" + d;
    }

    @PostMapping("/api/import/confirm")
    public Map<String, Object> confirmImport(@RequestParam Integer user_id, @RequestBody Map<String, Object> body) {
        Object value = body.get("transactions");
        if (!(value instanceof List<?> items) || items.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No transactions to import.");
        int[] counts = Objects.requireNonNull(transactions.execute(status -> {
            List<Map<String, Object>> categories = db.queryForList("SELECT category_id,category_type FROM Categories");
            Map<Integer, String> types = new HashMap<>();
            for (Map<String, Object> category : categories)
                types.put(((Number) category.get("category_id")).intValue(), Objects.toString(category.get("category_type")));
            Set<Integer> accountIds = new HashSet<>();
            for (Map<String, Object> account : db.queryForList("SELECT account_id FROM Accounts WHERE user_id=?", user_id))
                accountIds.add(((Number) account.get("account_id")).intValue());
            Map<Integer, BigDecimal> adjustments = new HashMap<>();
            int imported = 0;
            int skipped = 0;
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> raw)) { skipped++; continue; }
                @SuppressWarnings("unchecked") Map<String, Object> tx = (Map<String, Object>) raw;
                BigDecimal amount = decimal(tx.get("amount"));
                int category;
                int account;
                try {
                    category = positiveId(tx.get("category_id"));
                    account = positiveId(tx.get("account_id"));
                } catch (ResponseStatusException ex) { skipped++; continue; }
                String type = Objects.toString(tx.get("type"), "");
                String date = Objects.toString(tx.get("date"), "");
                if (amount.signum() <= 0 || !validDate(date) || !Set.of("Income", "Expense").contains(type)
                        || !accountIds.contains(account) || !type.equals(types.get(category))) {
                    skipped++;
                    continue;
                }
                insert("""
                        INSERT INTO Transactions (user_id,account_id,category_id,amount,transaction_type,payment_method,
                        description,transaction_date,source) VALUES (?,?,?,?,?,?,?,?,'import')
                        """, user_id, account, category, amount, type, text(tx, "paymentMethod", "Bank Transfer"),
                        text(tx, "description", "").isBlank() ? null : text(tx, "description", ""), date);
                BigDecimal delta = "Income".equals(type) ? amount : amount.negate();
                adjustments.merge(account, delta, BigDecimal::add);
                imported++;
            }
            adjustments.forEach((account, delta) ->
                    db.update("UPDATE Accounts SET balance=balance+? WHERE account_id=? AND user_id=?", delta, account, user_id));
            return new int[] { imported, skipped };
        }));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("imported", counts[0]);
        response.put("skipped", counts[1]);
        response.put("message", "Successfully imported " + counts[0] + " transaction" + (counts[0] == 1 ? "" : "s") + ".");
        return response;
    }

    private static boolean validDate(String value) {
        try { LocalDate.parse(value); return true; } catch (RuntimeException ex) { return false; }
    }

    @GetMapping("/api/analysis")
    public Map<String, Object> analysis(@RequestParam Integer user_id, @RequestParam(required = false) String month) {
        String selectedMonth = validMonth(month) ? month : YearMonth.now().format(MONTH_FORMAT);
        YearMonth period = YearMonth.parse(selectedMonth, MONTH_FORMAT);
        String priorMonth = period.minusMonths(1).format(MONTH_FORMAT);
        Map<String, Object> current = monthTotals(user_id, selectedMonth);
        Map<String, Object> prior = monthTotals(user_id, priorMonth);
        BigDecimal income = decimal(current.get("income"));
        BigDecimal expenses = decimal(current.get("expenses"));
        BigDecimal priorExpenses = decimal(prior.get("expenses"));
        BigDecimal savings = income.subtract(expenses);
        int savingsRate = income.signum() > 0 ? savings.multiply(BigDecimal.valueOf(100))
                .divide(income, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
        double expenseChange = priorExpenses.signum() > 0
                ? expenses.subtract(priorExpenses).multiply(BigDecimal.valueOf(100)).doubleValue() / priorExpenses.doubleValue() : 0;
        List<Map<String, Object>> categories = db.queryForList("""
                SELECT c.category_name,c.color,c.icon,COALESCE(SUM(t.amount),0) AS total,COUNT(t.transaction_id) AS tx_count
                FROM Transactions t JOIN Categories c ON t.category_id=c.category_id
                WHERE t.user_id=? AND t.transaction_type='Expense' AND DATE_FORMAT(t.transaction_date,'%Y-%m')=?
                GROUP BY c.category_id,c.category_name,c.color,c.icon ORDER BY total DESC
                """, user_id, selectedMonth);
        List<Map<String, Object>> prevCategories = db.queryForList("""
                SELECT c.category_name,COALESCE(SUM(t.amount),0) AS total FROM Transactions t
                JOIN Categories c ON t.category_id=c.category_id
                WHERE t.user_id=? AND t.transaction_type='Expense' AND DATE_FORMAT(t.transaction_date,'%Y-%m')=?
                GROUP BY c.category_name
                """, user_id, priorMonth);
        Map<String, BigDecimal> priorCategoryMap = new HashMap<>();
        for (Map<String, Object> row : prevCategories) priorCategoryMap.put(Objects.toString(row.get("category_name")), decimal(row.get("total")));
        Map<String, Object> average = db.queryForMap("""
                SELECT COALESCE(SUM(amount),0) AS expenses,COUNT(DISTINCT DATE_FORMAT(transaction_date,'%Y-%m')) AS months
                FROM Transactions WHERE user_id=? AND transaction_type='Expense'
                AND transaction_date>=DATE_SUB(STR_TO_DATE(CONCAT(?,'-01'),'%Y-%m-%d'),INTERVAL 4 MONTH)
                AND DATE_FORMAT(transaction_date,'%Y-%m')<>?
                """, user_id, selectedMonth, selectedMonth);
        BigDecimal avgExpenses = decimal(average.get("months")).signum() > 0
                ? decimal(average.get("expenses")).divide(decimal(average.get("months")), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        List<Map<String, Object>> topExpenses = db.queryForList("""
                SELECT t.description,t.amount,t.transaction_date,c.category_name,c.icon
                FROM Transactions t JOIN Categories c ON t.category_id=c.category_id
                WHERE t.user_id=? AND t.transaction_type='Expense' AND DATE_FORMAT(t.transaction_date,'%Y-%m')=?
                ORDER BY t.amount DESC LIMIT 5
                """, user_id, selectedMonth);
        List<Map<String, Object>> merchants = db.queryForList("""
                SELECT description,COUNT(*) AS frequency,SUM(amount) AS total_amount,MAX(transaction_date) AS last_date
                FROM Transactions WHERE user_id=? AND transaction_type='Expense' AND transaction_date>=DATE_SUB(CURDATE(),INTERVAL 3 MONTH)
                AND description IS NOT NULL AND description<>'' GROUP BY LOWER(description),description
                HAVING COUNT(*)>=2 ORDER BY frequency DESC LIMIT 8
                """, user_id);
        List<Map<String, Object>> insights = new ArrayList<>();
        if (priorExpenses.signum() > 0 && Math.abs(expenseChange) >= 5) {
            insights.add(insight(expenseChange > 10 ? "warning" : expenseChange > 0 ? "info" : "positive",
                    expenseChange > 0 ? "⚠️" : "✓", "Your total spending is " + String.format(Locale.ROOT, "%.1f", Math.abs(expenseChange))
                            + "% " + (expenseChange > 0 ? "higher" : "lower") + " than last month."));
        }
        if (savingsRate > 0) insights.add(insight("positive", "✓", "You saved " + savingsRate + "% of your income this month (₹" + savings + ")."));
        else if (savingsRate < 0) insights.add(insight("warning", "⚠️", "You spent more than you earned this month. Consider reviewing discretionary expenses."));
        for (Map<String, Object> category : categories.stream().limit(3).toList()) {
            String name = Objects.toString(category.get("category_name"));
            BigDecimal total = decimal(category.get("total"));
            BigDecimal old = priorCategoryMap.getOrDefault(name, BigDecimal.ZERO);
            double change = old.signum() == 0 ? 0 : total.subtract(old).multiply(BigDecimal.valueOf(100)).doubleValue() / old.doubleValue();
            int pct = expenses.signum() == 0 ? 0 : total.multiply(BigDecimal.valueOf(100)).divide(expenses, 0, java.math.RoundingMode.HALF_UP).intValue();
            if (old.signum() > 0 && change >= 20) insights.add(insight("warning", "⚠️", name + " spending increased by " + String.format(Locale.ROOT, "%.0f", change) + "% vs last month."));
            else if (old.signum() > 0 && change <= -15) insights.add(insight("positive", "✓", name + " spending reduced by " + String.format(Locale.ROOT, "%.0f", Math.abs(change)) + "% vs last month."));
            if (pct >= 30) insights.add(insight("info", "💡", name + " is your largest expense category at " + pct + "% of total spending."));
        }
        if (!merchants.isEmpty()) {
            Map<String, Object> top = merchants.getFirst();
            insights.add(insight("info", "🔄", "\"" + top.get("description") + "\" appeared " + top.get("frequency")
                    + " times in the last 3 months (total ₹" + top.get("total_amount") + ")."));
        }
        List<Map<String, Object>> categoryBreakdown = new ArrayList<>();
        for (Map<String, Object> category : categories) {
            String name = Objects.toString(category.get("category_name"));
            BigDecimal catTotal = decimal(category.get("total"));
            Map<String, Object> mapped = new LinkedHashMap<>(category);
            mapped.put("total", catTotal);
            mapped.put("priorTotal", priorCategoryMap.getOrDefault(name, BigDecimal.ZERO));
            mapped.put("pctOfTotal", expenses.signum() > 0 ? catTotal.multiply(BigDecimal.valueOf(100)).divide(expenses, 0, java.math.RoundingMode.HALF_UP).intValue() : 0);
            categoryBreakdown.add(mapped);
        }
        Map<String, Object> currentSummary = Map.of("income", income, "expenses", expenses, "savings", savings, "savingsRate", savingsRate);
        Map<String, Object> priorSummary = Map.of("income", decimal(prior.get("income")), "expenses", priorExpenses);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", selectedMonth);
        result.put("priorMonth", priorMonth);
        result.put("current", currentSummary);
        result.put("prior", priorSummary);
        result.put("threeMonthAvgExpenses", avgExpenses.setScale(0, java.math.RoundingMode.HALF_UP));
        result.put("expenseChangePct", BigDecimal.valueOf(expenseChange).setScale(1, java.math.RoundingMode.HALF_UP));
        result.put("categoryBreakdown", categoryBreakdown);
        result.put("topExpenses", topExpenses);
        result.put("frequentMerchants", merchants);
        result.put("insights", insights);
        return result;
    }

    private Map<String, Object> monthTotals(int user, String month) {
        return db.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN transaction_type='Income' THEN amount ELSE 0 END),0) AS income,
                COALESCE(SUM(CASE WHEN transaction_type='Expense' THEN amount ELSE 0 END),0) AS expenses
                FROM Transactions WHERE user_id=? AND DATE_FORMAT(transaction_date,'%Y-%m')=?
                """, user, month);
    }

    private static Map<String, Object> insight(String type, String icon, String text) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", type);
        value.put("icon", icon);
        value.put("text", text);
        return value;
    }

    @GetMapping("/api/spending-goals")
    public List<Map<String, Object>> spendingGoals(@RequestParam Integer user_id) {
        return db.queryForList("SELECT * FROM SpendingGoals WHERE user_id=? ORDER BY target_month DESC", user_id);
    }

    @PostMapping("/api/spending-goals")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSpendingGoal(@RequestBody Map<String, Object> body) {
        int user = userId(null, body);
        String targetMonth = text(body, "target_month", "");
        BigDecimal targetAmount = decimal(body.get("target_amount"));
        String basedOnMonth = text(body, "based_on_month", "");
        BigDecimal basedOnAmount = decimal(body.get("based_on_amount"));
        require(validMonth(targetMonth) && targetAmount.signum() > 0, "Please provide target month and target amount.");
        Map<String, Object> goalResult = Objects.requireNonNull(transactions.execute(status -> {
            db.update("""
                    INSERT INTO SpendingGoals (user_id,target_month,target_amount,based_on_month,based_on_amount)
                    VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE target_amount=VALUES(target_amount),
                    based_on_month=VALUES(based_on_month),based_on_amount=VALUES(based_on_amount)
                    """, user, targetMonth, targetAmount, basedOnMonth.isBlank() ? null : basedOnMonth,
                    basedOnAmount.signum() == 0 ? null : basedOnAmount);
            Map<String, Object> goal = db.queryForMap("SELECT goal_id FROM SpendingGoals WHERE user_id=? AND target_month=?", user, targetMonth);
            Object categoryTargets = body.get("category_targets");
            if (categoryTargets instanceof List<?> list && !list.isEmpty()) {
                db.update("DELETE FROM SpendingGoalCategories WHERE goal_id=?", goal.get("goal_id"));
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> raw)) continue;
                    int category;
                    try { category = positiveId(raw.get("category_id")); } catch (ResponseStatusException ex) { continue; }
                    BigDecimal amount = decimal(raw.get("target_amount"));
                    if (amount.signum() > 0) db.update("INSERT INTO SpendingGoalCategories (goal_id,category_id,target_amount) VALUES (?,?,?)",
                            goal.get("goal_id"), category, amount);
                }
            }
            return goal;
        }));
        return Map.of("id", goalResult.get("goal_id"), "message", "Spending goal created.");
    }

    @DeleteMapping("/api/spending-goals/{id}")
    public Map<String, Object> deleteSpendingGoal(@PathVariable int id, @RequestParam Integer user_id) {
        transactions.executeWithoutResult(status -> {
            db.update("DELETE FROM SpendingGoalCategories WHERE goal_id=?", id);
            db.update("DELETE FROM SpendingGoals WHERE goal_id=? AND user_id=?", id, user_id);
        });
        return result("Spending goal deleted.");
    }

    @GetMapping("/api/spending-goals/progress")
    public Map<String, Object> spendingGoalProgress(@RequestParam Integer user_id, @RequestParam(required = false) String month) {
        String selectedMonth = validMonth(month) ? month : YearMonth.now().format(MONTH_FORMAT);
        List<Map<String, Object>> goals = db.queryForList("SELECT * FROM SpendingGoals WHERE user_id=? AND target_month=?", user_id, selectedMonth);
        if (goals.isEmpty()) return Map.of("hasGoal", false, "month", selectedMonth);
        Map<String, Object> goal = goals.getFirst();
        BigDecimal target = decimal(goal.get("target_amount"));
        BigDecimal spent = total("""
                SELECT COALESCE(SUM(amount),0) FROM Transactions WHERE user_id=? AND transaction_type='Expense'
                AND DATE_FORMAT(transaction_date,'%Y-%m')=?
                """, user_id, selectedMonth);
        BigDecimal remaining = target.subtract(spent);
        int pct = target.signum() > 0 ? spent.multiply(BigDecimal.valueOf(100)).divide(target, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
        YearMonth period = YearMonth.parse(selectedMonth, MONTH_FORMAT);
        int passed = period.equals(YearMonth.now()) ? LocalDate.now().getDayOfMonth() : period.lengthOfMonth();
        int daysRemaining = Math.max(0, period.lengthOfMonth() - passed);
        BigDecimal avgDaily = passed > 0 ? spent.divide(BigDecimal.valueOf(passed), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal projected = avgDaily.multiply(BigDecimal.valueOf(period.lengthOfMonth()));
        String status = pct >= 100 ? "over_budget" : (pct >= 80 || projected.compareTo(target.multiply(new BigDecimal("1.05"))) > 0) ? "at_risk" : "on_track";
        List<Map<String, Object>> categoryGoals = db.queryForList("""
                SELECT sgc.category_id,sgc.target_amount,c.category_name,c.color,c.icon,
                COALESCE((SELECT SUM(t.amount) FROM Transactions t WHERE t.user_id=? AND t.category_id=sgc.category_id
                AND t.transaction_type='Expense' AND DATE_FORMAT(t.transaction_date,'%Y-%m')=?),0) AS spent
                FROM SpendingGoalCategories sgc JOIN Categories c ON sgc.category_id=c.category_id WHERE sgc.goal_id=?
                """, user_id, selectedMonth, goal.get("goal_id"));
        for (Map<String, Object> category : categoryGoals) {
            BigDecimal catTarget = decimal(category.get("target_amount"));
            BigDecimal catSpent = decimal(category.get("spent"));
            category.put("target_amount", catTarget);
            category.put("spent", catSpent);
            category.put("remaining", catTarget.subtract(catSpent));
            category.put("pct", catTarget.signum() > 0 ? catSpent.multiply(BigDecimal.valueOf(100)).divide(catTarget, 0, java.math.RoundingMode.HALF_UP).intValue() : 0);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hasGoal", true);
        response.put("month", selectedMonth);
        response.put("goal_id", goal.get("goal_id"));
        response.put("targetAmount", target);
        response.put("basedOnAmount", decimal(goal.get("based_on_amount")));
        response.put("currentSpent", spent);
        response.put("remaining", remaining);
        response.put("pctUsed", pct);
        response.put("daysPassed", passed);
        response.put("daysRemaining", daysRemaining);
        response.put("totalDays", period.lengthOfMonth());
        response.put("avgDailySpend", avgDaily.setScale(0, java.math.RoundingMode.HALF_UP));
        response.put("projectedMonthly", projected.setScale(0, java.math.RoundingMode.HALF_UP));
        response.put("projectedOverrun", projected.subtract(target).setScale(0, java.math.RoundingMode.HALF_UP));
        response.put("dailyBudget", daysRemaining > 0 ? remaining.divide(BigDecimal.valueOf(daysRemaining), 0, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
        response.put("status", status);
        response.put("statusLabel", status.equals("over_budget") ? "Over Budget" : status.equals("at_risk") ? "At Risk" : "On Track");
        response.put("statusIcon", status.equals("over_budget") ? "🔴" : status.equals("at_risk") ? "🟡" : "🟢");
        response.put("categoryGoals", categoryGoals);
        return response;
    }

    @GetMapping("/api/spending-goals/recommendations")
    public Map<String, Object> spendingRecommendations(@RequestParam Integer user_id,
            @RequestParam(required = false) String target_month, @RequestParam(required = false) BigDecimal target_amount) {
        String targetMonth = validMonth(target_month) ? target_month : YearMonth.now().format(MONTH_FORMAT);
        BigDecimal target = target_amount == null ? BigDecimal.ZERO : target_amount;
        List<Map<String, Object>> history = db.queryForList("""
                SELECT c.category_id,c.category_name,c.color,c.icon,AVG(monthly_total) AS avg_monthly,MAX(monthly_total) AS max_monthly
                FROM (
                  SELECT t.category_id,DATE_FORMAT(t.transaction_date,'%Y-%m') AS mon,SUM(t.amount) AS monthly_total
                  FROM Transactions t WHERE t.user_id=? AND t.transaction_type='Expense'
                  AND DATE_FORMAT(t.transaction_date,'%Y-%m')<>?
                  AND t.transaction_date>=DATE_SUB(CURDATE(),INTERVAL 3 MONTH)
                  GROUP BY t.category_id,mon
                ) sub JOIN Categories c ON sub.category_id=c.category_id
                GROUP BY c.category_id,c.category_name,c.color,c.icon HAVING avg_monthly>0 ORDER BY avg_monthly DESC
                """, user_id, targetMonth);
        if (history.isEmpty() || target.signum() <= 0)
            return Map.of("recommendations", List.of(), "totalCurrentEstimate", 0, "reductionNeeded", 0);
        BigDecimal estimate = history.stream().map(row -> decimal(row.get("avg_monthly"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reduction = estimate.subtract(target).max(BigDecimal.ZERO);
        Map<String, Integer> priorities = Map.of("Lifestyle", 1, "Entertainment", 1, "Travel", 1, "Food", 2,
                "Transportation", 2, "Bills", 3, "Health", 4, "Housing", 5, "Finance", 5, "Education", 4);
        history.sort((left, right) -> Integer.compare(
                priorities.getOrDefault(Objects.toString(left.get("category_name")), 2),
                priorities.getOrDefault(Objects.toString(right.get("category_name")), 2)));
        BigDecimal remaining = reduction;
        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (Map<String, Object> category : history) {
            if (remaining.signum() <= 0) break;
            BigDecimal average = decimal(category.get("avg_monthly")).setScale(0, java.math.RoundingMode.HALF_UP);
            String name = Objects.toString(category.get("category_name"));
            int priority = priorities.getOrDefault(name, 2);
            BigDecimal maxReduction = average.multiply(BigDecimal.valueOf(priority == 1 ? 30 : priority == 2 ? 20 : 10))
                    .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP);
            BigDecimal saving = maxReduction.min(remaining);
            if (saving.signum() <= 0) continue;
            Map<String, Object> recommendation = new LinkedHashMap<>();
            recommendation.put("category_id", category.get("category_id"));
            recommendation.put("category_name", name);
            recommendation.put("color", category.get("color"));
            recommendation.put("icon", category.get("icon"));
            recommendation.put("currentAvg", average);
            recommendation.put("suggestedTarget", average.subtract(saving));
            recommendation.put("potentialSaving", saving);
            recommendation.put("tip", categoryTip(name, saving));
            recommendations.add(recommendation);
            remaining = remaining.subtract(saving);
        }
        return Map.of("recommendations", recommendations, "totalCurrentEstimate", estimate.setScale(0, java.math.RoundingMode.HALF_UP),
                "reductionNeeded", reduction.setScale(0, java.math.RoundingMode.HALF_UP), "achievable", remaining.signum() <= 0);
    }

    private static String categoryTip(String category, BigDecimal saving) {
        String amount = saving.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        return switch (category) {
            case "Food" -> "Cooking at home a few more days per week and limiting food delivery to weekends could save ₹" + amount + ".";
            case "Lifestyle" -> "Setting a monthly shopping limit and delaying non-urgent purchases could reduce your Lifestyle spend by ₹" + amount + ".";
            case "Entertainment" -> "Reviewing streaming subscriptions and choosing one primary service could save you ₹" + amount + " per month.";
            case "Transportation" -> "Carpooling, using public transport more often, or combining trips could reduce transportation costs by ₹" + amount + ".";
            case "Travel" -> "Planning travel in advance and comparing hotels and flights could save ₹" + amount + ".";
            case "Bills" -> "Auditing subscriptions and switching to annual plans could reduce bills by ₹" + amount + ".";
            case "Health" -> "Using generics where prescribed and comparing pharmacy prices could reduce health costs by ₹" + amount + ".";
            default -> "Reviewing your " + category + " spending and identifying non-essential purchases could save ₹" + amount + ".";
        };
    }

    @GetMapping("/api/spending-goals/compare")
    public Map<String, Object> spendingGoalCompare(@RequestParam Integer user_id, @RequestParam(required = false) String month) {
        String selectedMonth = validMonth(month) ? month : YearMonth.now().format(MONTH_FORMAT);
        String priorMonth = YearMonth.parse(selectedMonth, MONTH_FORMAT).minusMonths(1).format(MONTH_FORMAT);
        List<Map<String, Object>> current = db.queryForList("""
                SELECT c.category_name,c.color,c.icon,COALESCE(SUM(t.amount),0) AS total
                FROM Transactions t JOIN Categories c ON t.category_id=c.category_id
                WHERE t.user_id=? AND t.transaction_type='Expense' AND DATE_FORMAT(t.transaction_date,'%Y-%m')=?
                GROUP BY c.category_name,c.color,c.icon
                """, user_id, selectedMonth);
        List<Map<String, Object>> previous = db.queryForList("""
                SELECT c.category_name,COALESCE(SUM(t.amount),0) AS total
                FROM Transactions t JOIN Categories c ON t.category_id=c.category_id
                WHERE t.user_id=? AND t.transaction_type='Expense' AND DATE_FORMAT(t.transaction_date,'%Y-%m')=?
                GROUP BY c.category_name
                """, user_id, priorMonth);
        Map<String, BigDecimal> previousAmounts = new HashMap<>();
        for (Map<String, Object> row : previous) previousAmounts.put(Objects.toString(row.get("category_name")), decimal(row.get("total")));
        List<Map<String, Object>> comparison = new ArrayList<>();
        for (Map<String, Object> row : current) {
            String name = Objects.toString(row.get("category_name"));
            BigDecimal now = decimal(row.get("total"));
            BigDecimal before = previousAmounts.getOrDefault(name, BigDecimal.ZERO);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("category_name", name);
            result.put("color", row.get("color"));
            result.put("icon", row.get("icon"));
            result.put("current", now);
            result.put("prior", before);
            result.put("change", before.signum() > 0 ? now.subtract(before).multiply(BigDecimal.valueOf(100))
                    .divide(before, 1, java.math.RoundingMode.HALF_UP) : null);
            comparison.add(result);
        }
        comparison.sort((a, b) -> decimal(b.get("current")).compareTo(decimal(a.get("current"))));
        return Map.of("month", selectedMonth, "priorMonth", priorMonth, "comparison", comparison);
    }

    @GetMapping("/api/insights")
    public Map<String, Object> insights(@RequestParam Integer user_id) {
        String month = YearMonth.now().format(MONTH_FORMAT);
        String priorMonth = YearMonth.now().minusMonths(1).format(MONTH_FORMAT);
        Map<String, Object> current = monthTotals(user_id, month);
        BigDecimal currentIncome = decimal(current.get("income"));
        BigDecimal currentExpenses = decimal(current.get("expenses"));
        BigDecimal priorExpenses = decimal(monthTotals(user_id, priorMonth).get("expenses"));
        BigDecimal savings = currentIncome.subtract(currentExpenses);
        int savingsRate = currentIncome.signum() > 0 ? savings.multiply(BigDecimal.valueOf(100))
                .divide(currentIncome, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
        double expenseChange = priorExpenses.signum() > 0
                ? currentExpenses.subtract(priorExpenses).multiply(BigDecimal.valueOf(100)).doubleValue() / priorExpenses.doubleValue() : 0;
        List<Map<String, Object>> categories = db.queryForList("""
                SELECT c.category_name,COALESCE(SUM(t.amount),0) AS total FROM Transactions t JOIN Categories c ON t.category_id=c.category_id
                WHERE t.user_id=? AND t.transaction_type='Expense' AND DATE_FORMAT(t.transaction_date,'%Y-%m')=?
                GROUP BY c.category_name ORDER BY total DESC LIMIT 1
                """, user_id, month);
        List<Map<String, Object>> goals = db.queryForList("SELECT target_amount FROM SpendingGoals WHERE user_id=? AND target_month=?", user_id, month);
        List<Map<String, Object>> result = new ArrayList<>();
        if (!goals.isEmpty()) {
            BigDecimal goal = decimal(goals.getFirst().get("target_amount"));
            int pct = goal.signum() > 0 ? currentExpenses.multiply(BigDecimal.valueOf(100)).divide(goal, 0, java.math.RoundingMode.HALF_UP).intValue() : 0;
            int monthDays = YearMonth.now().lengthOfMonth();
            int days = LocalDate.now().getDayOfMonth();
            BigDecimal projection = days > 0 ? currentExpenses.multiply(BigDecimal.valueOf(monthDays)).divide(BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
            if (pct >= 100) result.add(insight("warning", "🔴", "You've exceeded your monthly spending goal of ₹" + goal + "."));
            else if (projection.compareTo(goal.multiply(new BigDecimal("1.1"))) > 0)
                result.add(insight("warning", "⚠️", "At your current pace, you may exceed your ₹" + goal + " spending goal by ₹" + projection.subtract(goal).setScale(0, java.math.RoundingMode.HALF_UP) + "."));
            else result.add(insight("positive", "🎯", "You're on track for your spending goal. ₹" + goal.subtract(currentExpenses) + " remaining for " + Math.max(0, monthDays - days) + " days."));
        }
        if (Math.abs(expenseChange) >= 5)
            result.add(insight(expenseChange > 10 ? "warning" : "info", expenseChange > 0 ? "⚠️" : "✓",
                    "Spending is " + String.format(Locale.ROOT, "%.0f", Math.abs(expenseChange)) + "% " + (expenseChange > 0 ? "higher" : "lower") + " than last month."));
        if (!categories.isEmpty())
            result.add(insight("info", "💡", categories.getFirst().get("category_name") + " is your biggest expense this month at ₹" + categories.getFirst().get("total") + "."));
        if (savingsRate > 0) result.add(insight("positive", "✓", "You saved " + savingsRate + "% of income this month — keep it up!"));
        else if (currentIncome.signum() > 0 && currentExpenses.compareTo(currentIncome) > 0)
            result.add(insight("warning", "⚠️", "You spent ₹" + savings.abs() + " more than you earned this month."));
        return Map.of("insights", result, "month", month, "currentExpenses", currentExpenses, "currentIncome", currentIncome, "savings", savings, "savingsRate", savingsRate);
    }

}
