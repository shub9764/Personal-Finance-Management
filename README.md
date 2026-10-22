# Personal Finance Management Portal

A personal finance web application built with HTML, CSS, JavaScript, Java Spring Boot, and MySQL. It helps users manage their accounts, transactions, budgets, savings goals, bills, investments, and overall net worth.

## Tech Stack

- **Frontend:** HTML, CSS, JavaScript
- **Backend:** Java 21, Spring Boot
- **Build Tool:** Maven
- **Database:** MySQL

## Project Structure

```text
Personal-Finance-Management/
├── backend/
│   ├── src/main/java/       # Java backend code
│   ├── src/main/resources/  # Application configuration and SQL migrations
│   ├── pom.xml              # Maven dependencies and build settings
│   └── mvnw.cmd             # Maven wrapper for Windows
├── uploads/                 # Uploaded files
├── index.html               # Main webpage
├── style.css                # Website styling
├── script.js                # Frontend functionality and API requests
├── database.sql             # Initial database setup
├── .env.example             # Example environment configuration
├── .gitignore
└── README.md
```

## How to Run

### 1. Install the requirements

Make sure you have **Java 21** and **MySQL** installed.

### 2. Set up the database

Start MySQL and make sure the `finance_portal` database exists.

For a new database, you can run `database.sql` in MySQL Workbench.

**Note:** This script drops and recreates `finance_portal`, so do not run it if you already have data you need to keep.

### 3. Configure environment variables

Create a `.env` file in the project root and add your MySQL configuration:

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=finance_portal
DB_USER=root
DB_PASSWORD=YOUR_MYSQL_PASSWORD
PORT=3000
```

Replace `YOUR_MYSQL_PASSWORD` with your local MySQL password. Do not upload `.env` to GitHub.

### 4. Start the application

Open a terminal in the project folder and run:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Wait for Spring Boot to start, then open:

**http://localhost:3000**

To check the backend and database connection, visit:

http://localhost:3000/api/health

## Important Files

- `index.html`, `style.css`, and `script.js` — the website's frontend.
- `backend/` — the Java Spring Boot backend and API.
- `backend/pom.xml` — project dependencies and build configuration.
- `database.sql` — initial MySQL database schema.
- `.env` — local database credentials and configuration; keep it private.
