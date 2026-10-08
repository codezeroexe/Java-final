package com.loanmanagement.tui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;

class ScreenTest {

    private TerminalRenderer renderer;

    @BeforeEach
    void open() throws IOException {
        renderer = TerminalRenderer.virtual(50, 16);
    }

    @AfterEach
    void close() {
        renderer.close();
    }

    private static KeyStroke typed(char c) {
        return new KeyStroke(c, false, false);
    }

    private static final Screen BLANK = new Screen() {
        @Override
        public void render(TerminalRenderer renderer, Area area) {
        }

        @Override
        public Navigation onKey(KeyStroke key) {
            return Navigation.STAY;
        }
    };

    private static HomeScreen home(Screen target) {
        return new HomeScreen("Loan Management System",
                List.of("Apply for a loan after login.", "Run CIBIL first."),
                List.of(new HomeScreen.Entry("Register", () -> new Navigation.Push(target)),
                        new HomeScreen.Entry("Exit", Navigation.Quit::new)));
    }

    @Test
    void homeRendersHeaderTipsAndMenu() throws IOException {
        home(BLANK).render(renderer, new Area(0, 0, 50, 16));
        renderer.flush();
        assertEquals("╭", renderer.cell(0, 0));
        assertEquals("✻", renderer.cell(2, 1));
        assertEquals("T", renderer.cell(2, 3));
        assertEquals("A", renderer.cell(2, 4));
        assertEquals("❯", renderer.cell(0, 8));
    }

    @Test
    void homeDigitPushesMappedScreen() {
        assertInstanceOf(Navigation.Push.class, home(BLANK).onKey(typed('1')));
    }

    @Test
    void homeQuitKeyQuits() {
        assertInstanceOf(Navigation.Quit.class, home(BLANK).onKey(typed('q')));
    }

    @Test
    void applicationPushPopAndQuit() throws IOException, InterruptedException {
        TuiApplication app = new TuiApplication(renderer);
        assertEquals(false, app.apply(new Navigation.Push(BLANK)));
        app.apply(new Navigation.Push(BLANK));
        assertEquals(2, app.depth());
        assertEquals(false, app.apply(new Navigation.Pop()));
        assertEquals(1, app.depth());
        assertEquals(true, app.apply(new Navigation.Quit()));
    }

    @Test
    void applicationReplaceKeepsDepth() throws IOException {
        TuiApplication app = new TuiApplication(renderer);
        app.apply(new Navigation.Push(BLANK));
        app.apply(new Navigation.Push(BLANK));
        app.apply(new Navigation.Replace(BLANK));
        assertEquals(2, app.depth());
    }

    @Test
    void ctrlCInterruptsButPlainCDoesNot() {
        assertEquals(true, TuiApplication.isInterrupt(new KeyStroke('c', true, false)));
        assertEquals(false, TuiApplication.isInterrupt(new KeyStroke('c', false, false)));
    }

    @Test
    void popLastScreenEndsApplication() {
        TuiApplication app = new TuiApplication(renderer);
        app.apply(new Navigation.Push(BLANK));
        assertEquals(true, app.apply(new Navigation.Pop()));
    }
}
