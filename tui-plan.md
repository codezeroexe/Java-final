# Loan Management TUI Plan

## Goal

Replace the `Scanner` text menus with a full-screen terminal UI in the style of Claude Code: bordered boxes, a selectable menu list, a prompt box, a status line, and key hints. Services, repositories, validation, and the database schema stay unchanged. The TUI is a new presentation layer. The existing console app stays available as a fallback until the TUI reaches parity.

Visual reference: the mockup rendered in chat ("loan_console_tui_solid"). It shows the home screen, main menu, and loan list.

## Decisions to Confirm

1. **Library.** Recommended: **Lanterna 3.x** (cell-grid screen, keyboard input, box drawing, color). Confirm the latest 3.x release on Maven Central before adding it. Alternatives: JLine (strong input, weaker layout), or raw ANSI escape codes (no dependency, most custom work).
2. **Plan change.** `plan.md` says "console-only, `Scanner` for interactive input." A TUI library replaces `Scanner` in the presentation layer. Confirm this is an accepted deviation and document it in `plan.md` and `README.md`.
3. **Fallback.** Recommended: keep `ConsoleApplication` behind a flag (`--plain` or non-TTY detection) so the app still runs in dumb terminals and CI.
4. **Color.** Recommended: honor `NO_COLOR`, and use a single accent plus semantic colors (success, warning, danger) that map to the mockup.

## Architecture

```text
Main
 |
 +-- TuiApplication (new, cli/tui)
 |     screen stack, focus, key dispatch, render loop
 |     screens: Home, Login, Register, Onboarding, ForgotPassword,
 |              UserHome, LoanList, LoanApply, LoanDetail, Pan, Profile,
 |              AdminHome, UserSearch, UserDetail, UserEdit, LoanQueue, LoanReview
 |     components: MenuList, Box, PromptBox, Table, StatusLine, KeyHints, Dialog, TextField
 |
 +-- ConsoleApplication (unchanged, fallback)
 |
 +-- Services (unchanged): AuthenticationService, AdminUserService, LoanService,
       CibilService, PasswordResetService
```

Rules:
- Screens call services only. No SQL, no business rules in screens.
- Screens return results as values. Navigation is handled by the screen stack, not by recursive calls.
- All text goes through one width-aware measure function. Layout never counts `String.length()`.
- The renderer draws to a cell buffer and flushes diffs. Screens never write to the terminal directly.

## Phases

### Phase 0: Spike and decisions

- Resolve the decisions above.
- Build a throwaway screen that draws a box, a menu, and a line with wide characters (`₹`, `✻`, `●`, `❯`). Confirm borders stay aligned in macOS Terminal, iTerm2, and the IDE terminal.
- Confirm resize events re-layout without leaving stale cells.

**Exit check:** spike screen renders aligned on two terminals; decisions recorded in `plan.md` and `README.md`.

**Status (in progress):**
- Library: Lanterna **3.1.5** (latest stable; 3.2.0 is alpha). Added to `build.gradle`. Full suite still green.
- Width check via Lanterna virtual terminal: `₹`, `●`, `✻`, `❯`, and CJK (`漢字`, width 2) occupy the expected cells and align. Decomposed `e` + combining mark misaligns. Rule: NFC-normalize all text before layout.
- Throwaway spike removed from source.
- Open: aligned rendering on two real terminals (macOS Terminal, IDE terminal) not yet checked. Decisions 2–4 (`Scanner` deviation, fallback, color) still need user confirmation.

### Phase 1: Renderer foundation

- Cell buffer with width-aware text: wide characters take two cells, combining marks take zero.
- Box primitives (single border, optional title), padding, horizontal and vertical stacking, truncation with an ellipsis.
- Input: key events (arrows, Enter, Esc, Tab, Backspace, printable characters). Password fields render as `*` and never echo or log the value.
- Diff-based flush to the terminal, with cursor hidden during redraw and restored on exit.
- Restore the terminal (alternate screen, cursor, raw mode) on normal exit, exceptions, and Ctrl-C.

**Exit check:** unit tests render a buffer and compare cell contents for ASCII, wide, and combining text, box edges at fixed widths, truncation, and password masking. Terminal restores after a forced exception.

### Phase 2: Screen framework and components

- Screen interface: `render(area)`, `onKey(key)`, returns a `Navigation` value (push, pop, replace, quit).
- Screen stack owned by `TuiApplication`.
- Components: `MenuList` (`❯` marker, arrow and number selection), `PromptBox`, `TextField` (with validation message slot), `Table` (column widths, selected row marker), `StatusLine` (user, role, CIBIL, counts), `KeyHints` footer, `Dialog` (confirm and message).
- Home screen with the header box and tips, matching the mockup.

**Exit check:** menu navigation, field focus, table selection, and dialog confirm/cancel covered by tests that drive key sequences and assert on the rendered buffer.

### Phase 3: Account screens (Stage 1, Stage 4 onboarding)

- Root menu: Register, Login, CIBIL onboarding, Forgot password, Exit.
- Register and onboarding: multi-field form with per-field validation messages from `InputValidator`. Enter moves focus; the submit action runs only when all fields are valid.
- Login: identifier and masked password. Show attempts remaining and lockout messages from `AuthenticationResult`.
- Forgot password: request screen (same message for every input), then token and new password screen.
- Logout returns to the root menu.

**Exit check:** each validation message from the console flow appears in the TUI. Lockout and inactive states match `AuthenticationStatus`. Reset response does not reveal whether an account exists.

### Phase 4: User screens (Stage 3, Stage 4, Stage 5 views)

- User home: Apply, My loans, Loan details, PAN and CIBIL, My profile, Logout.
- My loans: table from the mockup (`#`, type, amount, status with colored dot, EMI). Enter opens details.
- Apply: wizard for amount, type (Home, Personal, Education, Vehicle), duration in months, annual income, purpose. Blocked with a clear message when CIBIL is missing.
- PAN and CIBIL: PAN input with format message; shows score and rate after assessment.
- Status line shows user, role, CIBIL score, and loan count.

**Exit check:** apply path produces the same `LoanSubmissionResult` as the console path. Missing-CIBIL block matches the service message. Table columns align with mixed-width amounts (`₹`) and status dots.

### Phase 5: Admin screens (Stage 2, Stage 3, Stage 5)

- Admin home: Search users, View user, Edit user, Activate, Deactivate, Delete, All loans, Loan details, Review loan.
- User search: field selector (first name, last name, email, mobile, username, status) and results table.
- User detail and edit: validated form; saves through `AdminUserService.updateUser`.
- Activate, deactivate, and delete use a confirm dialog. Delete requires explicit confirm and shows the anonymization note.
- Loan queue table, detail view, and review dialog (approve, reject with reason, needs more information). Approval shows the EMI summary before confirm.
- Email delivery failure shows the service message, not a generic success.

**Exit check:** regular users never see admin screens. Each admin action calls the same service method as the console path and reports the same `AdminActionResult`. Delete dialog cannot be confirmed by Enter alone without the explicit confirm key.

### Phase 6: Polish and fallback

- Responsive layout at 80×24 minimum. Below that, show a "terminal too small" screen instead of broken boxes.
- Color with `NO_COLOR` support. Layout stays readable without color (status uses text labels as well as dots).
- Plain fallback: non-TTY input or `--plain` runs `ConsoleApplication`.
- Help overlay bound to `?` lists the keys for the current screen.

**Exit check:** runs at 80×24 and at a large size without overlap. Output is readable with `NO_COLOR=1`. Piped input falls back to plain mode.

### Phase 7: Verification

- Renderer and component unit tests (Phase 1 and 2 checks) run in `gradle test` with no MySQL.
- Screen tests drive key sequences against fake services (same pattern as `AuthenticationServiceTest`).
- One integration pass against a local MySQL test DB: register, login, apply, admin review, logout, using the TUI.
- Manual pass in two terminals on macOS.

**Exit check:** `gradle clean test` green. Manual pass recorded in `README.md`.

## Testing Matrix

| Area | Minimum proof |
| --- | --- |
| Width handling | ASCII, `₹`, `✻`, `●`, combining marks align in a buffer test |
| Box and table | Right edge fixed for every row; truncation has an ellipsis |
| Keys | Arrows, Enter, Esc, Tab, Backspace, Ctrl-C restore terminal |
| Password fields | Masked, never echoed, cleared after submit |
| Auth flows | Same results as console for valid, invalid, locked, inactive |
| User flows | Apply, PAN, missing-CIBIL block, loan table |
| Admin flows | Role gate, search, edit, status change, delete confirm, review |
| Resize | Re-layout without stale cells; small-terminal screen |
| Fallback | Non-TTY runs plain console |

## Risks

- **Unicode width differences across terminals.** Mitigation: width function with explicit tests; avoid ambiguous-width glyphs in the default theme.
- **Terminal left in raw mode after a crash.** Mitigation: restore in `finally` and on shutdown hook; test with a forced exception.
- **Scope creep.** Mitigation: parity with console first; new visuals only after Phase 6.
- **Dependency footprint.** Mitigation: one library, pinned version, no transitive surprises (check with `gradle dependencies`).

## Completion Criteria

- All console user and admin flows reachable in the TUI with the same service calls and results.
- Renderer and screen tests pass without MySQL. Integration pass documented.
- Plain console fallback works. `README.md` covers how to launch the TUI and the fallback.
- `plan.md` and `README.md` record the `Scanner` deviation and library choice.
