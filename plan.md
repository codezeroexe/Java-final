# Java Loan Management System Plan

## Project Status

This workspace currently contains the five stage requirement PDFs and no Java source, build configuration, or project-level instruction files. This document is the implementation roadmap; it does not establish that any feature has been built.

## Goal and Constraints

Build a console-only Core Java loan management application that uses `Scanner` for interactive input and JDBC for all MySQL operations. Deliver requirements incrementally from Stage 1 through Stage 5.

In scope:

- User registration or credential generation, sign-in, sign-out, password reset, account status, and failed-login lockout.
- Database-backed admin authentication and user management, including audit logging.
- User loan applications and admin review, approval, rejection, and notifications.
- PAN validation, CIBIL score generation and persistence, and loan-type-based interest calculation.
- EMI, total interest, and total payable calculations when a loan is approved.
- Actual email delivery for the notifications specified in the stage documents.

Out of scope: frontend, web application, Spring Boot, Spring MVC, and Spring Security.

## Decisions to Confirm

Resolve these before implementing affected flows. Recommended defaults are listed to keep progress unblocked.

1. **Registration versus generated credentials:** Stage 1 asks users to choose a unique username and password. Stage 4 adds a CIBIL onboarding flow that generates both and overlaps registration. Recommended default: retain both as separate entry options backed by one user-creation service; generated onboarding supplies the generated-credential option.
2. **Emailing credentials:** Stage 4 requests sending generated username and password by email. Plaintext credential email is unsafe. Recommended default: store only a password hash, deliver a one-time temporary password once, require a password change at first login, and never log the temporary password. If the assignment requires the literal behavior, document this as an accepted security exception.
3. **Password reset proof:** Stage 1 only identifies a user by email or username before resetting. That is insufficient proof of ownership. Recommended default: send a short-lived reset code to the registered email before accepting a new password. Confirm whether assignment evaluation permits this extra step.
4. **Interest-rate policy:** Stage 4 gives rate ranges by loan type but no score-to-rate mapping. Implemented policy: scores 600–699 use maximum rate, 700–799 use midpoint, and 800–900 use minimum rate in each specified range. Treat this as assignment policy, not financial advice.
5. **Loan tenure units:** Normalize tenure to integer months in storage and calculations. Convert years at input time if years are accepted by the CLI.
6. **User deletion and loan history:** Stage 2 requests deletion and Stage 3/5 requires loan records. Implement soft deletion: anonymize personal fields, mark account inactive/deleted, retain loan and audit history.
7. **Environment/build:** Environment has JDK 25, Gradle 9.5.1, and MySQL 9.7 client; Maven is absent and local MySQL server is stopped. Use Gradle with Java 21 source/target compatibility. Start/configure MySQL before database-backed runs.

## Proposed Structure

Keep presentation, business rules, and persistence separate while avoiding framework layers:

```text
src/main/java/<package>/
  Main.java
  cli/                 Console menus, prompts, and input parsing
  model/               User, LoanApplication, enums, and value objects
  service/             Authentication, users, loans, pricing, notifications
  dao/                 JDBC repositories and row mapping
  db/                  Connection factory and schema migration runner
  validation/          Input and domain validation
  notification/        Email sender interface and SMTP implementation
src/main/resources/db/migration/
src/test/java/<package>/
pom.xml
README.md
.env.example
```

Use one `Scanner` for the process lifetime. CLI code gathers input and renders results; it does not contain SQL or business policy. Services enforce rules and coordinate transactions. DAOs use `PreparedStatement`, close JDBC resources with try-with-resources, and return domain objects rather than leaking `ResultSet` instances. Inject a connection provider and email sender where needed so services can be tested without live infrastructure.

## Data Model

Create versioned SQL migrations and apply them in order. Store timestamps consistently, preferably UTC, and map them to Java time types.

### `users`

- `id` primary key.
- `first_name`, `last_name`, `mobile`, `email`, `location`.
- `username` unique; `email` unique after case normalization.
- `password_hash`; never persist plaintext passwords.
- `role`: `ROLE_USER` or `ROLE_ADMIN`.
- `status`: at minimum `ACTIVE` and `INACTIVE`; a separate deletion marker may be needed depending on the deletion decision above.
- `failed_attempts` nonnegative integer, default `0`.
- `pan` nullable until supplied, with uniqueness policy to confirm.
- `cibil_score` nullable until generated; constrain generated values to 600–900 in service validation.
- `created_at`, `last_modified_at`; update modification time for every user mutation.

### `loan_applications`

- `id` primary key; `user_id` foreign key to `users`.
- `loan_amount`, `annual_income` as fixed-precision decimal values.
- `loan_type`, `loan_duration_months`, `interest_rate`, `purpose`.
- `status`: `PENDING`, `APPROVED`, `REJECTED`, `NEEDS_MORE_INFO`.
- `rejection_reason` nullable; `applied_at`, `reviewed_at`, and reviewer admin ID where available.
- `emi_amount`, `total_interest`, `total_payable` nullable until approval calculations complete.

### `admin_audit_log`

Record actor, action, target user or loan, timestamp, and non-sensitive action details. At minimum record confirmed user deletion as Stage 2 requires. Do not record passwords, PAN values, SMTP credentials, or other secrets.

Add indexes for unique username/email, user search fields used by admins, loan status, and loan-to-user lookup. Use foreign keys and explicit transaction boundaries. Final column types, indexes, and deletion behavior must be checked against the selected MySQL version.

## Implementation Phases

### Phase 0: Environment and Runnable Skeleton

- Verify JDK, Gradle, and MySQL availability; record versions in `README.md`.
- Create Java console entry point, package structure, build/test configuration, and environment-variable-based configuration.
- Add schema migration runner and initial database connection check.
- Provide `.env.example` with names only, never real credentials. Keep local secrets out of version control.

**Exit check:** clean build and tests pass; application can connect to a configured MySQL database and report a useful error when configuration or connection is missing.

### Phase 1: User Accounts and Authentication (Stage 1)

- Implement validated registration with required first name, last name, mobile, email, username, and password; location is captured when supplied and is required in CIBIL onboarding.
- Validate names as letters for CIBIL onboarding, mobile as exactly 10 digits, email format, required fields, and password policy (minimum 8 characters with uppercase, lowercase, digit, and special character).
- Enforce normalized unique username and email in both service checks and database constraints; handle concurrent duplicate registration cleanly.
- Set new user role to `ROLE_USER`, status to `ACTIVE`, failed attempts to zero, and creation/modification timestamps on the server.
- Hash passwords using a vetted JDK password KDF such as PBKDF2 with a per-password salt and appropriate work factor. Do not use a fast general-purpose hash.
- Support login by email or username; reject blank input, unknown accounts, invalid passwords, and inactive accounts with clear console feedback.
- On regular-user failure, atomically increment `failed_attempts`; after the third consecutive failure set status to `INACTIVE`. Report attempts remaining before lockout. On successful login, reset count to zero.
- Do not apply user-level attempt counters or lockout to admins, per Stage 2.
- Track authenticated session identity in application memory; present user menu and return to the root menu on logout.
- Implement password reset using the confirmed ownership-verification approach. Update `last_modified_at` after a successful change.

**Exit check:** unit tests cover every stated validator, login identifier, status check, lockout boundary, counter reset, logout transition, and password reset; JDBC integration tests prove uniqueness and persistent state.

### Phase 2: Admin Authentication and User Administration (Stage 2)

- Seed a predefined `ROLE_ADMIN` account through a documented one-time bootstrap process. Credentials must come from secure configuration, not source code or a committed SQL literal.
- Add role-checked admin menu and operations. Enforce authorization in services as well as menu visibility.
- Search users by first name, last name, email, mobile, username, and status; use parameterized queries and bounded result output.
- View user details, including contact/status and associated loan applications.
- Edit validated user information and update modification time.
- Activate or deactivate accounts. Reactivation is the manual admin path for a lockout; activation must not alter role or password.
- Confirm before deletion. Apply the approved deletion/history policy and write audit record in the same database transaction.
- Send email notifications after successful account activation, deactivation, deletion/anonymization, and edits. Database success must not be falsely reported as email success; surface delivery failure and provide retryable handling.

**Exit check:** regular users cannot invoke admin operations even if they reach service APIs; admin search/actions, audit behavior, status effects, and notifications pass service and JDBC integration tests.

### Phase 3: Loan Application and Review (Stage 3)

- Add user loan application menu available only to authenticated active users.
- Collect amount, type, duration, interest rate, annual income, and purpose; associate `user_id` from session, never from user-entered ID. Default status to `PENDING`.
- Require positive amount and income, positive duration, valid type, and nonblank required text. Persist using JDBC and confirm submission to user.
- Add admin loan queue and detail view with applicant name/contact, loan inputs, application date, and status.
- Support `APPROVED`, `REJECTED`, and `NEEDS_MORE_INFO` transitions as appropriate; require a rejection reason and record reviewer/time. Preserve a clear state-transition policy and prevent re-review from silently overwriting terminal decisions.
- Notify applicant by email after decision; include rejection reason for rejection. Stage 3 names approval and rejection email content explicitly.

**Exit check:** a user can submit and see their pending loan; admin can inspect it and transition it; unauthorized users cannot review or mutate another user's application; invalid amounts/durations never reach the database.

### Phase 4: PAN, CIBIL, and Interest Rates (Stage 4)

- Add loan-type enum with canonical values `HOME`, `PERSONAL`, `EDUCATION`, and `VEHICLE`; map CLI labels to enum values. Reconcile Stage 3 examples such as `Car` with `Vehicle`.
- Validate PAN using exact format: five uppercase letters, four digits, one uppercase letter. Reject invalid input with the specified message.
- Generate CIBIL score uniformly within inclusive range 600–900 and persist it for the user.
- Implement approved score-band-to-rate mapping for each loan type, bounded by Stage 4 ranges: Home 6.5–8.5%, Personal 9–12%, Education 4–6%, Vehicle 7–9%.
- Return the score and calculated rate with clear formatted output. Reuse the same rate policy when creating a loan application rather than accepting an unrelated arbitrary rate.
- Implement Stage 4 onboarding as an alternate account-creation path only after confirming its interaction with Stage 1 registration. Validate first/last name, mobile, email, location, and PAN before creating the account.
- Persist generated username/password hash, send the required credentials/onboarding email under the agreed security policy, generate and persist CIBIL score, then offer the loan application flow. Make partial failure states recoverable and never claim delivery or persistence succeeded when it did not.

**Exit check:** PAN boundaries, CIBIL min/max, every rate band boundary, and each loan type have deterministic tests. Random generation tests assert range, not a particular score.

### Phase 5: Approval Calculations and Final Notifications (Stage 5)

- On admin approval, use principal, annual interest rate, and tenure in months to calculate monthly EMI with the standard amortizing-loan formula:

  `EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)`

  where `P` is principal, `r` is monthly interest rate (`annual rate / 12 / 100`), and `n` is number of monthly payments. For zero interest, use `P / n`.

- Use decimal arithmetic with a documented currency scale and rounding mode. Compute total payable as rounded EMI times payment count and total interest as total payable minus principal; reconcile any final-payment rounding difference explicitly.
- In one transaction, ensure approval state, reviewer/time, EMI, total interest, and total payable are persisted consistently. Define behavior for recalculation and duplicate approval attempts.
- Send approval email containing sanctioned amount, type, tenure, rate, monthly EMI, total interest, and total payable. Rejection email remains supported.
- Show all loans and approval/rejection controls in admin console menu.

**Exit check:** verify known EMI examples, zero-rate handling, fractional rounding, persistence rollback on failure, and notification content. Email failures must leave an accurate approved record and be reported for retry.

### Phase 6: Hardening and Handoff

- Run all unit, JDBC integration, and end-to-end console scenario tests against a disposable test database.
- Test migrations from an empty database and from the prior migration version; document backup and migration behavior.
- Review authorization, prepared-statement use, password handling, secret management, audit-log redaction, and SMTP failure paths.
- Document prerequisites, database creation/configuration, admin bootstrap, migration/run/test commands, SMTP configuration, and known assignment decisions in `README.md`.
- Verify repository contains no secrets, generated binaries, or real user data.

**Exit check:** a fresh developer can configure database and mail credentials, migrate schema, launch console, complete user/admin/loan flows, and run tests from documented commands.

## Cross-Cutting Rules

- Use JDBC `PreparedStatement` for every query containing input; never concatenate user input into SQL.
- Keep business validation in services/domain rules, with database constraints as a second line of defense.
- Use transactions for multi-row state changes, failed-attempt updates, deletion/audit, loan decisions/calculations, and related persistence.
- Keep SMTP credentials and database credentials outside source control. Read runtime configuration from environment or ignored local configuration.
- Separate email formatting/delivery from database transactions. Email is an external side effect; report failures and provide retry strategy without rolling back already-committed business state.
- Do not log passwords, password hashes, PAN, or SMTP secrets. Minimize exposure of mobile/email in operational logs.
- Normalize email and username consistently for lookup and uniqueness. Keep display values appropriately formatted.
- Keep all menu loops recoverable from invalid input and end-of-input; avoid recursive menu calls and duplicate `Scanner` instances.
- Store monetary values in MySQL `DECIMAL` and calculate with `BigDecimal`, not binary floating point.

## Verification Matrix

| Area           | Minimum proof                                                                                                             |
| -------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Validation     | Required/blank values, malformed email, mobile length, password policy, PAN format, positive monetary and duration values |
| Authentication | Username/email login, unknown user, wrong password, inactive account, role gate, logout                                   |
| Lockout        | Two failures remain usable; third locks; success resets count; admin excluded                                             |
| Persistence    | Unique username/email, timestamps/defaults, JDBC parameterization, rollback of multi-step writes                          |
| Admin          | Search criteria, detail display, edits, activation/deactivation, delete confirmation, audit event                         |
| Loans          | User ownership, pending default, queue/details, rejection reason, valid state changes, email content                      |
| CIBIL/rates    | Inclusive 600–900 bounds, PAN acceptance/rejection, each loan type and agreed score band                                  |
| EMI            | Standard formula example, zero interest, decimal rounding, persisted totals, approval notification                        |
| Operations     | Fresh migration, missing config, DB outage, SMTP outage, no secret leakage                                                |

Use unit tests for deterministic validation and calculation rules; use JDBC integration tests for constraints, transactions, and account/loan state; use service tests with a fake email sender for notification decisions. Reserve live SMTP checks for an explicitly configured test mailbox.

## Requirement Traceability

| Source  | Planned coverage                                                                                                                  |
| ------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Stage 1 | Phase 1: registration, login, logout, reset, input validation, active default, timestamp, JDBC persistence, three-attempt lockout |
| Stage 2 | Phase 2: predefined admin, roles, user search/details/edit/status/delete, deletion audit, account notifications, role enforcement |
| Stage 3 | Phase 3: loan submission, pending default, admin review, approval/rejection/more-info, rejection reason, email                    |
| Stage 4 | Phase 4: PAN format, CIBIL 600–900, user fields, loan type/rate policy, generated credentials, onboarding email, loan offer       |
| Stage 5 | Phase 5: approve flow, EMI and totals, JDBC persistence, admin loan view/actions, approval email details                          |

## Completion Criteria

- All five stage requirements are implemented or have an explicitly documented, approved deviation.
- All phase exit checks pass, including database-backed tests for JDBC behavior.
- Console-only Core Java architecture is preserved; no Spring or frontend is introduced.
- `README.md` supports clean setup and execution without exposing credentials.
- Known ambiguities/security exceptions are resolved and documented before release.
