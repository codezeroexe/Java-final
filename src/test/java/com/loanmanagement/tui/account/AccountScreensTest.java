package com.loanmanagement.tui.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.notification.EmailSender;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.service.AuthenticationResult;
import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.AuthenticationStatus;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.InterestRatePolicy;
import com.loanmanagement.service.PasswordResetService;
import com.loanmanagement.service.RegisterCommand;
import com.loanmanagement.tui.screen.FormScreen;
import com.loanmanagement.tui.screen.HomeScreen;
import com.loanmanagement.tui.screen.MessageScreen;
import com.loanmanagement.tui.screen.Navigation;
import com.loanmanagement.tui.screen.Screen;
import com.loanmanagement.testsupport.InMemoryUserRepository;

class AccountScreensTest {

    /** Stack driver that applies navigation the way TuiApplication does, without a terminal. */
    private static final class Driver {
        private final Deque<Screen> stack = new ArrayDeque<>();

        Driver(Screen root) {
            stack.push(root);
        }

        Screen top() {
            return stack.peek();
        }

        int depth() {
            return stack.size();
        }

        void press(KeyType type) {
            apply(top().onKey(new KeyStroke(type)));
        }

        void type(String text) {
            for (char c : text.toCharArray()) {
                apply(top().onKey(new KeyStroke(c, false, false)));
            }
        }

        void enter(String text) {
            type(text);
            press(KeyType.Enter);
        }

        private void apply(Navigation navigation) {
            if (navigation instanceof Navigation.Push push) {
                stack.push(push.screen());
            } else if (navigation instanceof Navigation.Pop) {
                stack.pop();
            } else if (navigation instanceof Navigation.Replace replace) {
                stack.pop();
                stack.push(replace.screen());
            } else if (navigation instanceof Navigation.Quit) {
                stack.clear();
            }
        }
    }

    private InMemoryUserRepository users;
    private PasswordHasher hasher;
    private AuthenticationService authentication;
    private List<String> sentBodies;
    private AppContext context;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        hasher = new PasswordHasher();
        authentication = new AuthenticationService(users, hasher);
        sentBodies = new java.util.ArrayList<>();
        EmailSender sender = (recipient, subject, body) -> sentBodies.add(body);
        context = new AppContext(authentication,
                new PasswordResetService(users, hasher, sender),
                new CibilService(users, users, authentication, new InterestRatePolicy(), sender));
    }

    private void registerAsha() {
        authentication.register(new RegisterCommand("Asha", "Patel", "9876543210", "asha@example.com",
                "Pune", "asha", "Asha!Pass1".toCharArray()));
    }

    @Test
    void registerCreatesAccountThenReturnsToRoot() {
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("1");
        driver.enter("Asha");
        driver.enter("Patel");
        driver.enter("9876543210");
        driver.enter("asha@example.com");
        driver.enter("Pune");
        driver.enter("asha");
        driver.type("Asha!Pass1");
        driver.press(KeyType.Enter);

        assertInstanceOf(MessageScreen.class, driver.top());
        driver.press(KeyType.Enter);
        assertEquals(1, driver.depth());
        assertTrue(users.findByLoginIdentifier("asha").isPresent());
    }

    @Test
    void registerWeakPasswordShowsErrorAndStaysOnForm() {
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("1");
        driver.enter("Asha");
        driver.enter("Patel");
        driver.enter("9876543210");
        driver.enter("asha@example.com");
        driver.enter("Pune");
        driver.enter("asha");
        driver.type("weak");
        driver.press(KeyType.Enter);

        assertInstanceOf(FormScreen.class, driver.top());
        assertEquals(2, driver.depth());
        assertTrue(users.findByLoginIdentifier("asha").isEmpty());
    }

    @Test
    void loginSuccessOpensSessionAndLogoutReturnsToRoot() {
        registerAsha();
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("2");
        driver.enter("asha");
        driver.type("Asha!Pass1");
        driver.press(KeyType.Enter);

        assertInstanceOf(HomeScreen.class, driver.top());
        assertEquals(2, driver.depth());
        driver.press(KeyType.Enter);
        assertEquals(1, driver.depth());
    }

    @Test
    void loginWrongPasswordShowsAttemptsAndStays() {
        registerAsha();
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("2");
        driver.enter("asha");
        driver.type("Wrong!Pass1");
        driver.press(KeyType.Enter);

        assertInstanceOf(FormScreen.class, driver.top());
        AuthenticationResult check = authentication.authenticate("asha", "Asha!Pass1".toCharArray());
        assertEquals(AuthenticationStatus.SUCCESS, check.status());
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeSession() {
        users.createGenerated(new RegisterCommand("Asha", "Patel", "9876543210", "asha@example.com",
                "Pune", "asha_gen", "Temp!Pass1".toCharArray()), hasher.hash("Temp!Pass1".toCharArray()),
                "ABCDE1234F", 742);
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("2");
        driver.enter("asha_gen");
        driver.type("Temp!Pass1");
        driver.press(KeyType.Enter);

        assertInstanceOf(FormScreen.class, driver.top());
        assertEquals("Change temporary password", ((FormScreen) driver.top()).title());
        driver.enter("New!Pass2");
        assertInstanceOf(HomeScreen.class, driver.top());
        assertEquals(2, driver.depth());
    }

    @Test
    void forgotPasswordResetsAndAllowsNewLogin() {
        registerAsha();
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("4");
        driver.enter("asha");
        assertInstanceOf(FormScreen.class, driver.top());

        String body = sentBodies.get(sentBodies.size() - 1);
        String token = body.substring(body.lastIndexOf(' ') + 1);
        driver.enter(token);
        driver.enter("New!StrongPass2");
        assertInstanceOf(MessageScreen.class, driver.top());
        driver.press(KeyType.Enter);
        assertEquals(1, driver.depth());

        assertEquals(AuthenticationStatus.SUCCESS,
                authentication.authenticate("asha", "New!StrongPass2".toCharArray()).status());
    }

    @Test
    void resetWithBlankTokenDoesNotSubmit() {
        registerAsha();
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("4");
        driver.enter("asha");
        assertEquals(2, driver.depth());
        driver.press(KeyType.Enter);
        driver.press(KeyType.Enter);

        assertInstanceOf(FormScreen.class, driver.top());
        assertEquals(2, driver.depth());
    }

    @Test
    void onboardingCreatesAccountAndRoutesToTemporaryPasswordChange() {
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("3");
        driver.enter("Asha");
        driver.enter("Patel");
        driver.enter("9876543210");
        driver.enter("asha@example.com");
        driver.enter("Pune");
        driver.enter("ABCDE1234F");
        driver.press(KeyType.Enter);

        assertInstanceOf(MessageScreen.class, driver.top());
        driver.press(KeyType.Enter);
        assertEquals("Change temporary password", ((FormScreen) driver.top()).title());
    }

    @Test
    void rootExitQuits() {
        Driver driver = new Driver(AccountScreens.root(context));
        driver.type("5");
        assertEquals(0, driver.depth());
    }
}
