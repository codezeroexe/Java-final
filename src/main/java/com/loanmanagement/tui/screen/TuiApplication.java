package com.loanmanagement.tui.screen;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;

/** Owns the screen stack and the render loop. Screens never call each other directly. */
public final class TuiApplication {
    private static final long POLL_MILLIS = 30;
    /** Smallest terminal the layouts are built for. Smaller sizes show a notice instead. */
    public static final int MIN_COLUMNS = 80;
    public static final int MIN_ROWS = 24;

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
            if (dispatch(key.get())) {
                return;
            }
            draw();
        }
    }

    /**
     * Handles one key. Returns true when the application should quit. Ctrl-C always quits. F1 opens
     * the key help. When the terminal is too small, only quit keys are honored.
     */
    boolean dispatch(KeyStroke key) throws IOException {
        if (isInterrupt(key)) {
            return true;
        }
        if (tooSmall()) {
            return key.getKeyType() == KeyType.Character && key.getCharacter() != null
                    && Character.toLowerCase(key.getCharacter()) == 'q';
        }
        if (key.getKeyType() == KeyType.F1 && !stack.isEmpty()) {
            List<String> lines = stack.peek().keyHelp().stream()
                    .map(hint -> TextWidth.fit(hint.key(), 12) + hint.action())
                    .toList();
            return apply(new Navigation.Push(new com.loanmanagement.tui.screen.MessageScreen(
                    "Keys", lines, Tone.PLAIN, Navigation.Pop::new)));
        }
        return apply(stack.peek().onKey(key));
    }

    private boolean tooSmall() throws IOException {
        return renderer.columns() < MIN_COLUMNS || renderer.rows() < MIN_ROWS;
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

    /** Renders the top screen, or the too-small notice. Package-private for tests. */
    void draw() throws IOException {
        if (stack.isEmpty()) {
            return;
        }
        renderer.clear();
        if (tooSmall()) {
            renderer.text(0, 0, "Terminal too small.", Tone.WARNING);
            renderer.text(0, 1, "Need " + MIN_COLUMNS + "×" + MIN_ROWS + ", have "
                    + renderer.columns() + "×" + renderer.rows() + ".", Tone.MUTED);
            renderer.text(0, 2, "Resize the window, or press q to quit.", Tone.MUTED);
        } else {
            stack.peek().render(renderer, new Area(0, 0, renderer.columns(), renderer.rows()));
        }
        renderer.flush();
    }
}
