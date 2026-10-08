package com.loanmanagement.tui.screen;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import com.googlecode.lanterna.input.KeyStroke;
import com.loanmanagement.tui.render.TerminalRenderer;

/** Owns the screen stack and the render loop. Screens never call each other directly. */
public final class TuiApplication {
    private static final long POLL_MILLIS = 30;

    private final TerminalRenderer renderer;
    private final Deque<Screen> stack = new ArrayDeque<>();

    public TuiApplication(TerminalRenderer renderer) {
        this.renderer = renderer;
    }

    public void run(Screen first) throws IOException, InterruptedException {
        stack.push(first);
        draw();
        while (!stack.isEmpty()) {
            Optional<KeyStroke> key = renderer.pollKey();
            if (key.isEmpty()) {
                if (renderer.resized()) {
                    draw();
                }
                Thread.sleep(POLL_MILLIS);
                continue;
            }
            if (isInterrupt(key.get())) {
                return;
            }
            if (apply(stack.peek().onKey(key.get()))) {
                return;
            }
            draw();
        }
    }

    /**
     * Ctrl-C always exits. In raw mode the terminal sends it as a key, not as SIGINT, so screens
     * must never be able to swallow it.
     */
    static boolean isInterrupt(KeyStroke key) {
        return key.isCtrlDown() && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'c';
    }

    /** Applies a navigation result. Returns true when the application should quit. */
    boolean apply(Navigation navigation) {
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
        return stack.isEmpty();
    }

    /** Depth of the screen stack. Exposed for tests. */
    int depth() {
        return stack.size();
    }

    private void draw() throws IOException {
        if (stack.isEmpty()) {
            return;
        }
        renderer.clear();
        stack.peek().render(renderer, new Area(0, 0, renderer.columns(), renderer.rows()));
        renderer.flush();
    }
}
