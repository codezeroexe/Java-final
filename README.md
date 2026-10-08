# Java Loan Management System

Console-based loan management application built with Core Java, `Scanner`, JDBC, and MySQL. No frontend or Spring framework. Requirements: [Stage 1](Stage%201.pdf), [Stage 2](Stage%202.pdf), [Stage 3](Stage%203.pdf), [Stage 4](Stage%204.pdf), and [Stage 5](Stage%205.pdf). Implementation roadmap: [`plan.md`](plan.md).

## Features

- Register with username/password or use CIBIL onboarding to generate credentials.
- Log in with username or email; lock regular-user account after three consecutive failures.
- Reset passwords with a one-time token emailed to the registered address; tokens expire after 15 minutes.
- Store salted PBKDF2 password hashes, not plaintext passwords. Generated temporary passwords must be changed at first login.
- Bootstrap an admin from environment variables; enforce admin role in service operations.
- Search, view, edit, activate, deactivate, and soft-delete users. Record admin operations in audit table.
- Validate PAN format, generate and store mock CIBIL score, and calculate interest rate by loan type and score band.
- Submit loan applications, view loan status, and let admins approve, reject, or request more information.
- Calculate EMI and totals on approval; send configured SMTP notifications for account and loan events.

## Requirements

- JDK 21 or newer. Project targets Java 21; locally verified with JDK 25.
- Gradle 9.5 or newer.
- MySQL 8.0.16 or newer, with server running.
- SMTP account for actual email notifications. SMTP is not needed to compile or run unit tests.

Gradle downloads MySQL Connector/J, Jakarta Mail, and JUnit dependencies from Maven Central.

## Configure MySQL

Create database and application account using a MySQL administrator account:

```sql
CREATE DATABASE loan_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'loan_app'@'localhost' IDENTIFIED BY 'choose-a-local-password';
GRANT ALL PRIVILEGES ON loan_management.* TO 'loan_app'@'localhost';
```

Set connection and initial-admin variables in your shell. Use your own credentials; never commit them:

```sh
export DB_URL='jdbc:mysql://localhost:3306/loan_management'
export DB_USER='loan_app'
export DB_PASSWORD='choose-a-local-password'
export ADMIN_USERNAME='admin'
export ADMIN_EMAIL='admin@example.com'
export ADMIN_PASSWORD='choose-a-strong-password'
```

At startup, application applies `V1__initial_schema.sql`. Database itself must already exist. First startup creates admin only when all three `ADMIN_*` values are configured. Later startups do not reset an existing admin password. `.env.example` lists supported variables but is not loaded automatically.

For outgoing mail, configure SMTP host, port, login, password, and sender address:

```sh
export SMTP_HOST='smtp.example.com'
export SMTP_PORT='587'
export SMTP_USER='your-smtp-login'
export SMTP_PASSWORD='your-smtp-password'
export SMTP_FROM='loans@example.com'
```

SMTP uses STARTTLS. If email delivery fails, completed database changes remain saved and console reports notification failure. CIBIL onboarding shows temporary password once in current console session if its email cannot be delivered.

## Build, Test, Run

From project root:

```sh
gradle clean test
gradle installDist
build/install/loan-management-system/bin/loan-management-system
```

Start the app from the installed script. In testing, `gradle run` did not pass the terminal through to the app, so the full-screen interface did not start that way.

Startup connects to MySQL, applies schema migration, and seeds the initial admin when configured. When run in an interactive terminal, the full-screen interface opens. Add `--plain` to use the line-based console menu instead. The plain menu is also used when input or output is not a terminal. `gradle test` runs unit tests without requiring MySQL. Database integration and live SMTP delivery require their respective services and credentials.

### Full-screen interface

- Move with arrow keys. Choose a numbered item with its digit (1 to 9) or Enter.
- In forms, Tab or arrow down moves to the next field, Enter moves on or submits from the last field, and Esc goes back.
- F1 shows the keys for the current screen.
- Ctrl-C exits from any screen. The terminal must be at least 80 by 24; smaller sizes show a resize notice.
- Set `NO_COLOR=1` to turn off colors. Status is also shown in text, so colors are not required.

## Console Workflows

Root menu offers:

1. **Register:** enter required profile fields, username, and password.
2. **Login:** use username or email and password.
3. **CIBIL onboarding:** enter profile and PAN; app generates username, temporary password, CIBIL score, and rate, then offers authenticated user menu.
4. **Forgot password:** enter username/email, retrieve emailed token, then provide token and new password. Response does not reveal whether account exists.

Regular-user menu supports loan application, loan history, PAN/CIBIL assessment, and logout. Loan applications require a saved CIBIL score. Admin menu supports user search/details/edit/status/delete plus loan listing/review.

Loan duration is entered in months. Loan types are Home, Personal, Education, and Vehicle.

## How It Works

```text
ConsoleApplication / AdminConsoleMenu
			  |
			  v
	 Service classes
 authentication | admin users | CIBIL | loans | EMI/rates
			  |
			  v
   JDBC repositories ----> MySQL
			  |
			  +----------> SMTP email sender
```

- `cli`: prompts, menus, and output. One `Scanner` is shared by menus.
- `service`: business rules, validation decisions, roles, lockout, rates, and workflow coordination.
- `dao`: JDBC queries using `PreparedStatement`; repositories map database rows to model records.
- `db`: connection configuration and startup schema migration.
- `model`: user, loan, status, and loan-type records/enums.
- `security`, `validation`, `notification`: password hashing, input rules, and SMTP delivery.

### Main Database Tables

- `users`: profile, normalized login identifiers, password hash, role/status, failed attempts, PAN, and CIBIL score.
- `loan_applications`: applicant, loan terms, review state, EMI, and totals.
- `admin_audit_log`: actor, action, target, and timestamp.
- `schema_migrations`: records applied schema version.

Account deactivation sets status to inactive. User deletion anonymizes personal fields and retains related loans/audit history so references remain valid.

## Design Choices

- **Services and repositories:** menu code handles input; services enforce rules; JDBC repositories own SQL. This makes core rules testable without a live database.
- **Password storage:** `PasswordHasher` uses PBKDF2-HMAC-SHA256 with random per-password salt and constant-time comparison.
- **Lockout:** failed-attempt update runs under row lock (`SELECT ... FOR UPDATE`) so concurrent failures do not lose increments. Third failed regular-user login sets status inactive; successful login clears counter. Admins are excluded.
- **SQL safety:** user-provided values are bound as prepared-statement parameters. Dynamic admin search columns come only from a fixed enum.
- **Money:** MySQL `DECIMAL` and Java `BigDecimal` avoid binary floating-point rounding. EMI uses monthly interest and number of monthly payments; currency rounds to two decimals using `HALF_UP`.
- **CIBIL/rates:** score is randomly generated in inclusive range 600–900; this is a project simulation, not a credit bureau lookup. Rates are assignment policy: 600–699 gets maximum range rate, 700–799 midpoint, and 800–900 minimum.
- **Email:** mail is sent after database commit. SMTP is an external system, so a mail failure is reported without undoing a successful account or loan update.
- **Admin seed:** first admin is created from environment values, hashed before storage, and is never inserted with a hard-coded credential.

## Viva Preparation

Practice explaining the flow in your own words; avoid presenting simulated behavior as a real bank or credit-bureau integration.

**What does the application do?**
It is a Java console system for registering users, managing roles/accounts, submitting loans, reviewing applications, calculating EMI, and notifying users by email.

**Why Core Java and JDBC instead of Spring?**
The stage requirements specify a console application and JDBC. Direct JDBC keeps the database path visible and avoids frameworks outside the requested scope.

**How is code organized?**
The CLI reads input, services contain business rules, repositories execute SQL, and models carry data. `Main` wires these components together.

**How is a password protected?**
The app generates a random salt and derives a PBKDF2-HMAC-SHA256 hash. The salt and work-factor are stored with the hash; login derives a candidate hash and compares bytes in constant time. Plaintext is not stored.

**How does account lockout work?**
Each failed regular-user login increments a database counter under a row lock. Third consecutive failure changes status to inactive. A successful login resets the counter. Admin login does not use this counter.

**How do you prevent SQL injection?**
All external values are passed to JDBC `PreparedStatement` placeholders. Search column names are selected from an enum, not accepted as SQL text.

**How is admin-only behavior enforced?**
Admin menus are shown only after admin login, and each admin service also checks active status and `ROLE_ADMIN`. Hiding a menu is not the authorization boundary.

**How are loan states handled?**
New applications start `PENDING`. Admin review can approve, reject with reason, or request more information. Final review writes status, reviewer, calculated values, and audit entry together.

**How is EMI calculated?**
For principal `P`, monthly rate `r = annualRate / 12 / 100`, and `n` months: `EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)`. Zero-rate loans use `P / n`. Rounded EMI determines total payable and total interest.

**Is CIBIL score real?**
No. Score is randomly simulated between 600 and 900, validated, and saved. PAN check validates format only; it does not verify PAN ownership or contact a government service.

**How are rates chosen?**
Each loan type has range from requirement document. Implemented score bands choose maximum, midpoint, or minimum of that range. This deterministic mapping is project-defined because source requirements do not specify score bands.

**Why soft-delete users?**
It anonymizes personal data while preserving loan foreign keys and audit history. Account is inactive and cannot log in.

**What happens if email fails after approval?**
Committed database state remains correct. Console reports delivery failure; retry handling is a future improvement rather than pretending the transaction rolled back.

**How does password reset avoid account enumeration?**
Request always returns same message. Active account gets a cryptographically random token by email; only SHA-256 token digest is stored. Token is single-use and expires after 15 minutes. Password update and token consumption share one transaction.

**How would you test it further?**
Add MySQL integration tests for constraints, lockout transactions, migrations, audit writes, and loan review; add a test SMTP mailbox for message delivery. Current unit tests cover validation, hashing, auth/lockout, rates, EMI, and loan-service rules.

## Current Limitations

- CIBIL generation and rate bands are educational simulation only.
- No real loan eligibility decision or external credit check is performed.
- Verified: `gradle clean test` passes all 16 tests against a local MySQL 9.7 server (JDBC integration tests use `loan_management_test`, user `loan_test`). The console app starts, applies migration, and creates the bootstrap admin against the same database.
- Not verified: live SMTP delivery. Integration tests use a no-op email sender.
- Email retry queue and migration rollback tooling are not included. Failed notifications are reported on the console only.
