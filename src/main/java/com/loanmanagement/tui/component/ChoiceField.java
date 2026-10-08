package com.loanmanagement.tui.component;

import java.util.List;
import java.util.function.Function;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;
import com.loanmanagement.tui.screen.Area;

/** Picks one option from a fixed list with left and right arrows. Wraps at both ends. */
public final class ChoiceField<T> implements Field {
    private final String label;
    private final List<T> options;
    private final Function<T, String> display;
    private int index;

    public ChoiceField(String label, List<T> options, Function<T, String> display) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("A choice field needs at least one option");
        }
        this.label = label;
        this.options = List.copyOf(options);
        this.display = display;
    }

    public T value() {
        return options.get(index);
    }

    @Override
    public int height() {
        return 1;
    }

    @Override
    public boolean handle(KeyStroke key) {
        int count = options.size();
        if (key.getKeyType() == KeyType.ArrowLeft) {
            index = (index - 1 + count) % count;
            return true;
        }
        if (key.getKeyType() == KeyType.ArrowRight) {
            index = (index + 1) % count;
            return true;
        }
        return false;
    }

    @Override
    public void render(TerminalRenderer renderer, Area area, boolean focused) {
        String line = label + ": < " + display.apply(value()) + " >";
        renderer.text(area.column(), area.row(),
                TextWidth.fit(line, area.width()), focused ? Tone.ACCENT : Tone.PLAIN);
    }
}
