package com.loanmanagement.tui.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.model.LoanType;
import com.loanmanagement.model.User;
import com.loanmanagement.notification.EmailSender;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.service.AdminUserService;
import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.EmiCalculator;
import com.loanmanagement.service.InterestRatePolicy;
import com.loanmanagement.service.LoanApplicationCommand;
import com.loanmanagement.service.LoanService;
import com.loanmanagement.service.PasswordResetService;
import com.loanmanagement.service.RegisterCommand;
import com.loanmanagement.tui.account.AppContext;
import com.loanmanagement.tui.account.Session;
import com.loanmanagement.tui.screen.DialogScreen;
import com.loanmanagement.tui.screen.FormScreen;
import com.loanmanagement.tui.screen.HomeScreen;
import com.loanmanagement.tui.screen.MessageScreen;
import com.loanmanagement.testsupport.InMemoryLoanRepository;
import com.loanmanagement.testsupport.InMemoryUserRepository;
import com.loanmanagement.testsupport.ScreenDriver;

class AdminScreensTest {
    private InMemoryUserRepository users;
    private InMemoryLoanRepository loanRepo;
    private AuthenticationService authentication;
    private AppContext ctx;
    private User admin;
    private User asha;
    private Session session;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        PasswordHasher hasher = new PasswordHasher();
        authentication = new AuthenticationService(users, hasher);
        EmailSender sender = (recipient, subject, body) -> { };
        InterestRatePolicy rates = new InterestRatePolicy();
        loanRepo = new InMemoryLoanRepository();
        ctx = new AppContext(authentication,
                new PasswordResetService(users, hasher, sender),
                new CibilService(users, users, authentication, rates, sender),
                new LoanService(loanRepo, rates, new EmiCalculator(), sender),
                new AdminUserService(users, sender));
        users.createAdmin("admin", "admin@example.com", hasher.hash("Admin!Pass1".toCharArray()));
        admin = users.findByLoginIdentifier("admin").orElseThrow();
        asha = authentication.register(new RegisterCommand("Asha", "Patel", "9876543210", "asha@example.com",
                "Pune", "asha", "Asha!Pass1".toCharArray()));
        session = new Session(admin);
    }

    private ScreenDriver dashboard() {
        return new ScreenDriver(AdminScreens.home(ctx, session));
    }

    @Test
    void dashboardShowsAdminStatusLine() {
        assertInstanceOf(HomeScreen.class, dashboard().top());
        assertTrue(session.statusLine().contains("ROLE_ADMIN"));
    }

    @Test
    void searchOpensResultsThenUserDetail() {
        ScreenDriver driver = dashboard();
        driver.type("1");
        driver.press(KeyType.Enter);
        driver.type("Asha");
        driver.press(KeyType.Enter);

        assertInstanceOf(UserTableScreen.class, driver.top());
        assertEquals(2, driver.depth());
        driver.press(KeyType.Enter);
        assertInstanceOf(MessageScreen.class, driver.top());
        assertEquals(3, driver.depth());
        driver.press(KeyType.Enter);
        assertInstanceOf(UserTableScreen.class, driver.top());
    }

    @Test
    void deactivateChangesStatusAndReportsResult() {
        ScreenDriver driver = dashboard();
        driver.type("5");
        driver.enter(String.valueOf(asha.id()));

        assertInstanceOf(MessageScreen.class, driver.top());
        assertEquals(AccountStatus.INACTIVE, users.findActiveUser(asha.id()).orElseThrow().status());
        driver.press(KeyType.Enter);
        assertEquals(1, driver.depth());
    }

    @Test
    void deleteIsNotConfirmedByEnter() {
        ScreenDriver driver = dashboard();
        driver.type("6");
        driver.enter(String.valueOf(asha.id()));
        assertInstanceOf(DialogScreen.class, driver.top());

        driver.press(KeyType.Enter);
        assertEquals(1, driver.depth());
        assertEquals("Asha", users.findActiveUser(asha.id()).orElseThrow().firstName());
    }

    @Test
    void deleteConfirmedByExplicitKeyAnonymizes() {
        ScreenDriver driver = dashboard();
        driver.type("6");
        driver.enter(String.valueOf(asha.id()));
        driver.type("d");

        assertInstanceOf(MessageScreen.class, driver.top());
        assertEquals("Deleted", users.findActiveUser(asha.id()).orElseThrow().firstName());
    }

    @Test
    void approvalPreviewsEmiThenConfirmsAndStoresDecision() {
        var loan = loanRepo.create(asha.id(), new LoanApplicationCommand(new BigDecimal("500000"),
                LoanType.HOME, 240, new BigDecimal("1200000"), "Home purchase"), new BigDecimal("8.00"));
        ScreenDriver driver = dashboard();
        driver.type("9");
        driver.enter(String.valueOf(loan.id()));
        driver.press(KeyType.Enter);
        driver.press(KeyType.Enter);
        assertInstanceOf(DialogScreen.class, driver.top());

        driver.press(KeyType.Enter);
        assertInstanceOf(MessageScreen.class, driver.top());
        var reviewed = loanRepo.findById(loan.id()).orElseThrow();
        assertEquals(LoanStatus.APPROVED, reviewed.status());
        assertTrue(reviewed.emiAmount() != null);
    }

    @Test
    void rejectWithoutReasonStaysOnForm() {
        var loan = loanRepo.create(asha.id(), new LoanApplicationCommand(new BigDecimal("100000"),
                LoanType.PERSONAL, 12, new BigDecimal("500000"), "Travel"), new BigDecimal("10.00"));
        ScreenDriver driver = dashboard();
        driver.type("9");
        driver.enter(String.valueOf(loan.id()));
        driver.press(KeyType.ArrowRight);
        driver.press(KeyType.Enter);
        driver.press(KeyType.Enter);

        assertInstanceOf(FormScreen.class, driver.top());
        assertEquals(2, driver.depth());
        assertEquals(LoanStatus.PENDING, loanRepo.findById(loan.id()).orElseThrow().status());
    }

    @Test
    void rejectWithReasonStoresReason() {
        var loan = loanRepo.create(asha.id(), new LoanApplicationCommand(new BigDecimal("100000"),
                LoanType.PERSONAL, 12, new BigDecimal("500000"), "Travel"), new BigDecimal("10.00"));
        ScreenDriver driver = dashboard();
        driver.type("9");
        driver.enter(String.valueOf(loan.id()));
        driver.press(KeyType.ArrowRight);
        driver.press(KeyType.Enter);
        driver.enter("Income too low");

        assertInstanceOf(MessageScreen.class, driver.top());
        var reviewed = loanRepo.findById(loan.id()).orElseThrow();
        assertEquals(LoanStatus.REJECTED, reviewed.status());
        assertEquals("Income too low", reviewed.rejectionReason());
    }
}
