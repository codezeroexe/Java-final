package com.loanmanagement.tui.screen;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Supplier;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.component.MenuList;
import com.loanmanagement.tui.component.StatusBar;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;

/** Header box with tips, a numbered menu, and key hints. Matches the home mockup. */
public final class HomeScreen implements Screen {
    private static final int MAX_WIDTH = 60;

    /** A menu row and the action it starts. The action is built lazily, so screens are created on demand. */
    public record Entry(String label, Supplier<Navigation> action) {
    }

    private final String title;
    private final List<String> tips;
    private final List<Entry> entries;
    private final MenuList menu;
    private final Supplier<String> status;

    public HomeScreen(String title, List<String> tips, List<Entry> entries) {
        this(title, tips, entries, null);
    }

    /** {@code status} is drawn on the row above the key hints. Null hides it. */
    public HomeScreen(String title, List<String> tips, List<Entry> entries, Supplier<String> status) {
        this.title = title;
        this.tips = List.copyOf(tips);
        this.entries = List.copyOf(entries);
        this.menu = new MenuList(entries.stream().map(Entry::label).toList());
        this.status = status;
    }

    @Override
    public void render(TerminalRenderer renderer, Area area) {
        int boxWidth = Math.min(area.width(), MAX_WIDTH);
        int boxHeight = tips.size() + 5;
        renderer.box(area.column(), area.row(), boxWidth, boxHeight, null, Tone.MUTED);
        int textColumn = area.column() + 2;
        int textWidth = boxWidth - 4;
        renderer.text(textColumn, area.row() + 1,
                TextWidth.fit("✻ " + title, textWidth), Tone.WARNING);
        renderer.text(textColumn, area.row() + 3, TextWidth.fit("Tips", textWidth), Tone.MUTED);
        for (int i = 0; i < tips.size(); i++) {
            renderer.text(textColumn, area.row() + 4 + i, TextWidth.fit(tips.get(i), textWidth), Tone.PLAIN);
        }

        int menuRow = area.row() + boxHeight + 1;
        int hintRow = area.row() + area.height() - 1;
        int menuBottom = status == null ? hintRow - 1 : hintRow - 2;
        menu.render(renderer, new Area(area.column(), menuRow, area.width(), Math.max(0, menuBottom - menuRow)));
        if (status != null) {
            StatusBar.facts(renderer, new Area(area.column(), hintRow - 1, area.width(), 1),
                    List.of(status.get()));
        }
        StatusBar.hints(renderer, new Area(area.column(), hintRow, area.width(), 1), List.of(
                new StatusBar.Hint("↑↓", "move"),
                new StatusBar.Hint("Enter", "select"),
                new StatusBar.Hint("q", "quit")));
    }

    @Override
    public List<StatusBar.Hint> keyHelp() {
        return List.of(new StatusBar.Hint("↑↓", "move"), new StatusBar.Hint("Enter", "select"),
                new StatusBar.Hint("0-9", "choose"), new StatusBar.Hint("q", "quit"),
                new StatusBar.Hint("Ctrl-C", "exit"));
    }

    @Override
    public Navigation onKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'q') {
            return new Navigation.Quit();
        }
        OptionalInt chosen = menu.handle(key);
        if (chosen.isPresent() && chosen.getAsInt() < entries.size()) {
            return entries.get(chosen.getAsInt()).action().get();
        }
        return Navigation.STAY;
    }
}
