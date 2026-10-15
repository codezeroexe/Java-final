# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

No Gradle wrapper is committed; use a system `gradle` (9.5+) and JDK 21+ (project targets Java 21).

```sh
gradle clean test                                              # all tests
gradle test --tests 'com.loanmanagement.service.LoanServiceTest'           # one class
gradle test --tests 'com.loanmanagement.service.LoanServiceTest.someMethod' # one method
gradle installDist && build/install/loan-management-system/bin/loan-management-system   # run app (needs MySQL + DB_* env vars)
build/install/loan-management-system/bin/loan-management-system --plain   # line-based console instead of the TUI
gradle jar                                                     # build/libs/loan-management-system-1.0.0.jar
```

- `JdbcIntegrationTest` hardcodes `jdbc:mysql://127.0.0.1:3306/loan_management_test` with user `loan_test` / `loan_test_pass`, and has no skip guard. It fails when MySQL is down or that DB/user is missing. Unit tests need no database. To run only those, filter out `com.loanmanagement.integration.*`.
- `gradle run` did not pass the terminal through in testing; use the installDist start script for the TUI.
- Runtime config comes from environment variables only (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `SMTP_*`). `.env.example` is not loaded automatically. The database itself must exist; the app creates tables via migration.

## Architecture

Console-only Core Java 21 app: `Scanner` for input, plain JDBC for MySQL, Jakarta Mail for SMTP. No frameworks. Requirements live in `Stage 1–5.pdf`; `plan.md` is the roadmap. Its "Project Status" section is stale (it says no Java source exists).

Layering, enforced by convention (not by modules):

- `cli/` (`ConsoleApplication`, `AdminConsoleMenu`) reads input and prints. It holds no SQL or business rules. One shared `Scanner` lives for the process.
- `service/` holds business rules and workflow: auth and lockout, password reset, admin user management, loan submission and review, CIBIL onboarding, EMI and rate policy. Services take repository interfaces and `EmailSender` through constructors, so tests inject fakes.
- `dao/` has repository interfaces (`UserRepository`, `LoanRepository`, `CibilRepository`, `AdminUserRepository`, `AdminBootstrapRepository`) with `Jdbc*` implementations. All SQL uses `PreparedStatement`. Dynamic search columns come from the `UserSearchField` enum, never from raw input.
- `db/`: `ConnectionFactory` builds connections from env. `SchemaMigrator` runs at startup and applies `src/main/resources/db/migration/V1__initial_schema.sql` once, tracked in `schema_migrations`. Add later migrations by bumping `CURRENT_VERSION` and adding a new SQL file. The migrator currently only handles one version, so it needs extending before V2 works.
- `notification/`: `EmailSender` interface with `SmtpEmailSender` (STARTTLS). `EmailDeliveryException` marks failures.
- `security/PasswordHasher`: PBKDF2-HMAC-SHA256, per-password salt, constant-time compare.
- `validation/InputValidator`: input format rules.
- `model/`: records and enums (`User`, `LoanApplication`, `LoanType`, `LoanStatus`, `UserRole`, `AccountStatus`).

Full-screen TUI lives in `tui/` (Lanterna 3.1.5): `render/` is the cell renderer and width-aware text helpers, `screen/` holds the `Screen` interface, `Navigation` results, the stack-based `TuiApplication` loop, and shared screens, `component/` holds form fields, `Table`, `Dialog`, and `StatusBar`, `account/` and `admin/` hold the flows that call the same services as the console. `TuiLauncher` wires services the way `Main` does and runs before the terminal is taken over. `Main` picks the TUI when stdin and stdout are a terminal, and the console otherwise or with `--plain`. Design and phases: `tui-plan.md`.

`Main` is the only composition root for the console path. It wires every service by hand, runs migration, seeds the admin, then starts `ConsoleApplication`. Add new collaborators there.

### Behaviour that spans files

- **Lockout:** a failed regular-user login runs `SELECT ... FOR UPDATE` on the user row. The third failure sets status inactive. Admins are exempt. A successful login resets the counter.
- **Email after commit:** services commit DB changes first, then send mail. A send failure is reported but does not roll back. CIBIL onboarding shows the temporary password once if its email fails.
- **Mail retry:** `OutboxEmailSender` wraps SMTP for account and loan notices only. A failed send is stored in `pending_notifications` (migration V2). Admins resend via the TUI dashboard or console option 11 (`NotificationService`). Password-reset and credential mails are never queued, since the queue is plaintext in the database.
- **Soft delete:** deleting a user anonymizes personal fields and keeps loans and audit rows, so foreign keys stay valid.
- **Money:** `DECIMAL` in MySQL, `BigDecimal` in Java. Rounding is `HALF_UP` to 2 places. `EmiCalculator` handles zero-rate loans as `P / n`.
- **CIBIL and rates:** CIBIL score is random in 600–900. This is a simulation, not a bureau lookup. `InterestRatePolicy` maps score bands to a rate range: 600–699 max, 700–799 midpoint, 800–900 min. The band mapping is project policy, not from the stage PDFs.
- **Admin authorization:** admin menus are hidden by login, but each admin service also checks active status and `ROLE_ADMIN`. Checks in services are the real boundary.
- **Admin bootstrap:** `AdminBootstrapper` creates the first admin from `ADMIN_*` env vars only when all three are set. Later runs do not change an existing admin password.
