package com.loanmanagement.tui.render;

import java.io.IOException;
import java.util.Optional;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;

/**
 * Cell-grid renderer over Lanterna. Screens draw here; only this class touches the terminal.
 * Text is NFC-normalized and clipped to the cell width before it is written.
 */
public final class TerminalRenderer implements AutoCloseable {

    /** Semantic colors. Mapped to ANSI so the terminal theme decides the exact shade. */
    public enum Tone {
        PLAIN(TextColor.ANSI.DEFAULT),
        MUTED(TextColor.ANSI.BLACK_BRIGHT),
        ACCENT(TextColor.ANSI.CYAN),
        SUCCESS(TextColor.ANSI.GREEN),
        WARNING(TextColor.ANSI.YELLOW),
        DANGER(TextColor.ANSI.RED);

        private final TextColor color;

        Tone(TextColor color) {
            this.color = color;
        }
    }

    private final Screen screen;
    private final boolean colorEnabled;
    private boolean closed;

    private TerminalRenderer(Screen screen, boolean colorEnabled) {
        this.screen = screen;
        this.colorEnabled = colorEnabled;
    }

    /** Opens the real terminal in alternate-screen mode. Restored on close and at JVM shutdown. */
    public static TerminalRenderer openTerminal() throws IOException {
        Screen screen = new DefaultTerminalFactory().createScreen();
        screen.startScreen();
        TerminalRenderer renderer = new TerminalRenderer(screen, System.getenv("NO_COLOR") == null);
        Runtime.getRuntime().addShutdownHook(new Thread(renderer::close));
        return renderer;
    }

    /** In-memory terminal for tests. Colors are off so cell text can be compared directly. */
    public static TerminalRenderer virtual(int columns, int rows) throws IOException {
        Screen screen = new TerminalScreen(new DefaultVirtualTerminal(new TerminalSize(columns, rows)));
        screen.startScreen();
        return new TerminalRenderer(screen, false);
    }

    public int columns() {
        return screen.getTerminalSize().getColumns();
    }

    public int rows() {
        return screen.getTerminalSize().getRows();
    }

    /** True when the terminal size changed since the last call. Caller should relayout. */
    public boolean resized() throws IOException {
        return screen.doResizeIfNecessary() != null;
    }

    public void clear() {
        screen.clear();
    }

    /** Writes text at a cell position, clipped to the right edge. Out-of-range positions are ignored. */
    public void text(int column, int row, String text, Tone tone) {
        if (column < 0 || row < 0 || row >= rows() || column >= columns()) {
            return;
        }
        String clipped = TextWidth.clip(text, columns() - column);
        TextGraphics graphics = screen.newTextGraphics();
        graphics.setForegroundColor(colorEnabled ? tone.color : TextColor.ANSI.DEFAULT);
        graphics.putString(new TerminalPosition(column, row), clipped);
    }

    /**
     * Draws a rounded box. The title sits in the top border. Boxes smaller than 4x2 are skipped,
     * since they cannot hold a border and one line of content.
     */
    public void box(int column, int row, int width, int height, String title, Tone tone) {
        if (width < 4 || height < 2) {
            return;
        }
        int inner = width - 2;
        String top;
        if (title == null || title.isBlank()) {
            top = "╭" + "─".repeat(inner) + "╮";
        } else {
            String label = " " + TextWidth.clip(title, inner - 4) + " ";
            int dashes = inner - 1 - TextWidth.width(label);
            top = "╭─" + label + "─".repeat(dashes) + "╮";
        }
        text(column, row, top, tone);
        for (int y = row + 1; y < row + height - 1; y++) {
            text(column, y, "│", tone);
            text(column + width - 1, y, "│", tone);
        }
        text(column, row + height - 1, "╰" + "─".repeat(inner) + "╯", tone);
    }

    /** Pushes the back buffer to the terminal as a diff. */
    public void flush() throws IOException {
        screen.refresh();
    }

    /** Non-blocking key read. Empty when no key is waiting. */
    public Optional<KeyStroke> pollKey() throws IOException {
        return Optional.ofNullable(screen.pollInput());
    }

    /** Blocks until a key arrives. */
    public KeyStroke readKey() throws IOException {
        return screen.readInput();
    }

    /** Character at a cell in the last flushed frame. Empty for the filler cell after a wide character. */
    public String cell(int column, int row) {
        return screen.getFrontCharacter(new TerminalPosition(column, row)).getCharacterString();
    }

    /**
     * Restores the terminal. Safe to call more than once and from the shutdown hook after a normal
     * close, since the screen may already be stopped.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            screen.stopScreen();
        } catch (IOException | RuntimeException ignored) {
            // The terminal is already gone or already restored; nothing left to do.
        }
    }
}
