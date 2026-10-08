package com.loanmanagement.tui.screen;

import java.util.List;
import java.util.function.Supplier;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.component.StatusBar;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;

/** A boxed message with one dismiss action. Enter or Escape runs {@code onDismiss}. */
public final class MessageScreen implements Screen {
    private final String title;
    private final List<String> lines;
    private final Tone tone;
    private final Supplier<Navigation> onDismiss;

    public MessageScreen(String title, List<String> lines, Tone tone, Supplier<Navigation> onDismiss) {
        this.title = title;
        this.lines = List.copyOf(lines);
        this.tone = tone;
        this.onDismiss = onDismiss;
    }

    @Override
    public void render(TerminalRenderer renderer, Area area) {
        int height = lines.size() + 4;
        int width = Math.min(area.width(), 60);
        if (area.height() < height + 1 || width < 6) {
            return;
        }
        renderer.box(area.column(), area.row(), width, height, title, tone);
        for (int i = 0; i < lines.size(); i++) {
            renderer.text(area.column() + 2, area.row() + 1 + i,
                    TextWidth.fit(lines.get(i), width - 4), Tone.PLAIN);
        }
        StatusBar.hints(renderer, new Area(area.column(), area.row() + height + 1, area.width(), 1),
                List.of(new StatusBar.Hint("Enter", "continue")));
    }

    @Override
    public Navigation onKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Enter || key.getKeyType() == KeyType.Escape) {
            return onDismiss.get();
        }
        return Navigation.STAY;
    }
}
