# Personal Finance Management Portal

Java 21, Spring Boot, Maven, and MySQL backend with the existing browser frontend.

# Run the Java backend (recommended)

The existing HTML, CSS, and JavaScript frontend is served by the Spring Boot backend. MySQL remains on port `3306`; Spring Boot listens on port `3000`.

1. Start MySQL and make sure the existing `finance_portal` database is available. Do not rerun `database.sql` against a database containing data: it begins with `DROP DATABASE`.
2. Configure the project-root `.env` without committing it:

   ```env
   DB_HOST=localhost
   DB_PORT=3306
   DB_NAME=finance_portal
   DB_USER=root
   DB_PASSWORD=YOUR_MYSQL_PASSWORD
   PORT=3000
   ```

3. Install Java 21. The Maven wrapper downloads and runs Maven 3.9.9. From the project root:

   ```powershell
   $env:JAVA_HOME = Split-Path (Split-Path (Get-Command java.exe).Source -Parent) -Parent
   cd backend
   .\mvnw.cmd clean package
   .\mvnw.cmd spring-boot:run
   ```

4. Open <http://localhost:3000>. Check the database connection at <http://localhost:3000/api/health>.

Spring Boot loads connection settings from the project-root `.env` when launched from `backend`. Hibernate is configured with `ddl-auto=validate`; it will not create, drop, or update tables. The additive migration `backend\src\main\resources\db\migration\V1__spending_goals.sql` defines the two spending-goal tables used by the existing API. It is safe to execute more than once and does not modify existing records.

## Database setup

Your old `personalfinance` database is not used by this project.

The project uses:

`finance_portal`

The included `database.sql` deliberately resets **only** `finance_portal` and creates a clean schema. It does not touch `personalfinance`.

### Initialize a new database only

Open MySQL Workbench and open (only when intentionally creating a new, empty `finance_portal` database):

`database.sql`

Execute the whole file.

The file begins with `DROP DATABASE IF EXISTS finance_portal` and erases that database. Do not run it against an existing installation; use only an explicitly reviewed additive migration when needed.

It creates:

- Users
- Accounts
- Categories
- Transactions
- Budgets
- SavingsGoals
- Bills
- Investments
- Assets
- Liabilities
- SpendingGoals
- SpendingGoalCategories

There are no sample accounts, transactions, budgets, goals, bills, investments, assets or liabilities.

Only basic transaction categories are created because the transaction form needs category choices.

### Add your own user

After executing the SQL, run this in MySQL Workbench:

```sql
USE finance_portal;

INSERT INTO Users (full_name, email)
VALUES ('YOUR NAME', 'YOUR EMAIL');
```

Replace `YOUR NAME` and `YOUR EMAIL` with your own values.

Then check:

```sql
SELECT * FROM Users;
```

## Application behavior

- Accounts are created from the Accounts page.
- The transaction Account dropdown is populated only from the Accounts table.
- Transactions update the selected account balance.
- Deleting a transaction reverses its balance effect.
- Budgets are entered by the user and spending is calculated from transactions.
- Savings on the dashboard is calculated as current-month income minus current-month expenses.
- Goals, bills and investments are entered by the user.
- Net worth is calculated from accounts + investments + other assets - liabilities.
- Reports are calculated from stored transactions.
- The Finance Assistant answers using stored finance data.
- Settings are loaded from and saved to the Users table.
