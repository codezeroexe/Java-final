package com.loanmanagement.tui.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;

class TuiApplicationTest {
    private TerminalRenderer renderer;

    private void open(int columns, int rows) throws IOException {
        renderer = TerminalRenderer.virtual(columns, rows);
    }

    @AfterEach
    void close() {
        if (renderer != null) {
            renderer.close();
        }
    }

    private static Screen blank() {
        return new Screen() {
            @Override
            public void render(TerminalRenderer renderer, Area area) {
                renderer.text(0, 0, "Blank screen", com.loanmanagement.tui.render.TerminalRenderer.Tone.PLAIN);
            }

            @Override
            public Navigation onKey(KeyStroke key) {
                return Navigation.STAY;
            }
        };
    }

    @Test
    void f1OpensKeyHelpAndEscapeReturns() throws IOException, InterruptedException {
        open(TuiApplication.MIN_COLUMNS, TuiApplication.MIN_ROWS);
        TuiApplication app = new TuiApplication(renderer);
        app.apply(new Navigation.Push(blank()));

        assertFalse(app.dispatch(new KeyStroke(KeyType.F1)));
        assertEquals(2, app.depth());
        assertFalse(app.dispatch(new KeyStroke(KeyType.Escape)));
        assertEquals(1, app.depth());
    }

    @Test
    void tooSmallTerminalShowsNoticeAndIgnoresKeysExceptQuit() throws IOException {
        open(TuiApplication.MIN_COLUMNS - 1, TuiApplication.MIN_ROWS);
        TuiApplication app = new TuiApplication(renderer);
        app.apply(new Navigation.Push(blank()));

        app.draw();
        assertEquals("T", renderer.cell(0, 0));
        assertFalse(app.dispatch(new KeyStroke('x', false, false)));
        assertTrue(app.dispatch(new KeyStroke('q', false, false)));
    }

    @Test
    void ctrlCQuitsFromAnyScreen() throws IOException {
        open(TuiApplication.MIN_COLUMNS, TuiApplication.MIN_ROWS);
        TuiApplication app = new TuiApplication(renderer);
        app.apply(new Navigation.Push(blank()));

        assertTrue(app.dispatch(new KeyStroke('c', true, false)));
    }

    @Test
    void normalSizeRendersTopScreen() throws IOException {
        open(TuiApplication.MIN_COLUMNS, TuiApplication.MIN_ROWS);
        TuiApplication app = new TuiApplication(renderer);
        app.apply(new Navigation.Push(blank()));

        app.draw();
        assertEquals("B", renderer.cell(0, 0));
    }
}
