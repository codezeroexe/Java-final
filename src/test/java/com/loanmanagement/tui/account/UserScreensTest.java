package com.loanmanagement.tui.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.model.User;
import com.loanmanagement.notification.EmailSender;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.service.AdminUserService;
import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.EmiCalculator;
import com.loanmanagement.service.InterestRatePolicy;
import com.loanmanagement.service.LoanService;
import com.loanmanagement.service.NotificationService;
import com.loanmanagement.testsupport.InMemoryNotificationRepository;
import com.loanmanagement.service.PasswordResetService;
import com.loanmanagement.service.RegisterCommand;
import com.loanmanagement.testsupport.InMemoryLoanRepository;
import com.loanmanagement.testsupport.InMemoryUserRepository;
import com.loanmanagement.testsupport.ScreenDriver;
import com.loanmanagement.tui.screen.FormScreen;
import com.loanmanagement.tui.screen.HomeScreen;
import com.loanmanagement.tui.screen.MessageScreen;

class UserScreensTest {
    private AuthenticationService authentication;
    private AppContext context;
    private User asha;
    private Session session;

    @BeforeEach
    void setUp() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        PasswordHasher hasher = new PasswordHasher();
        authentication = new AuthenticationService(users, hasher);
        EmailSender sender = (recipient, subject, body) -> { };
        InterestRatePolicy rates = new InterestRatePolicy();
        context = new AppContext(authentication,
                new PasswordResetService(users, hasher, sender),
                new CibilService(users, users, authentication, rates, sender),
                new LoanService(new InMemoryLoanRepository(), rates, new EmiCalculator(), sender),
                new AdminUserService(users, sender),
                new NotificationService(new InMemoryNotificationRepository(), sender));
        asha = authentication.register(new RegisterCommand("Asha", "Patel", "9876543210", "asha@example.com",
                "Pune", "asha", "Asha!Pass1".toCharArray()));
        session = new Session(asha);
    }

    private ScreenDriver userHome() {
        return new ScreenDriver(UserScreens.home(context, session));
    }

    private void fillApplication(ScreenDriver driver) {
        driver.enter("500000");
        driver.press(KeyType.Enter);
        driver.enter("240");
        driver.enter("1200000");
        driver.type("Home purchase");
    }

    @Test
    void applyIsBlockedUntilCibilAssessed() {
        ScreenDriver driver = userHome();
        driver.type("1");
        fillApplication(driver);
        driver.press(KeyType.Enter);

        assertInstanceOf(FormScreen.class, driver.top());
        assertEquals(2, driver.depth());
    }

    @Test
    void cibilAssessmentThenApplySubmitsPendingLoan() {
        ScreenDriver driver = userHome();
        driver.type("3");
        driver.enter("ABCDE1234F");
        driver.press(KeyType.Enter);
        assertInstanceOf(MessageScreen.class, driver.top());
        driver.press(KeyType.Enter);
        assertInstanceOf(HomeScreen.class, driver.top());
        assertTrue(session.user().cibilScore() != null);

        driver.type("1");
        fillApplication(driver);
        driver.press(KeyType.Enter);
        assertInstanceOf(MessageScreen.class, driver.top());
        driver.press(KeyType.Enter);

        assertEquals(1, session.loanCount());
        assertEquals(1, driver.depth());
    }

    @Test
    void invalidAmountStaysOnApplyForm() {
        ScreenDriver driver = userHome();
        driver.type("1");
        driver.enter("abc");
        assertInstanceOf(FormScreen.class, driver.top());
        assertEquals(2, driver.depth());
    }

    @Test
    void myLoansShowsEmptyStateThenDetailsForEachApplication() {
        ScreenDriver driver = userHome();
        driver.type("2");
        assertInstanceOf(LoansScreen.class, driver.top());
        assertEquals(0, session.loanCount());
        driver.press(KeyType.Escape);
        assertInstanceOf(HomeScreen.class, driver.top());
    }

    @Test
    void loanDetailOpensFromTableAndReturns() {
        ScreenDriver driver = userHome();
        driver.type("3");
        driver.enter("ABCDE1234F");
        driver.press(KeyType.Enter);
        driver.press(KeyType.Enter);
        driver.type("1");
        fillApplication(driver);
        driver.press(KeyType.Enter);
        driver.press(KeyType.Enter);

        driver.type("2");
        assertInstanceOf(LoansScreen.class, driver.top());
        driver.press(KeyType.Enter);
        assertInstanceOf(MessageScreen.class, driver.top());
        assertEquals(3, driver.depth());
        driver.press(KeyType.Enter);
        assertInstanceOf(LoansScreen.class, driver.top());
    }

    @Test
    void profileShowsReadOnlyDetailsThenReturns() {
        ScreenDriver driver = userHome();
        driver.type("4");
        assertInstanceOf(MessageScreen.class, driver.top());
        driver.press(KeyType.Enter);
        assertEquals(1, driver.depth());
    }

    @Test
    void logoutReturnsToRoot() {
        ScreenDriver driver = userHome();
        driver.type("5");
        assertEquals(0, driver.depth());
    }
}
