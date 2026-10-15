package com.loanmanagement.tui.component;

import java.util.List;
import java.util.OptionalInt;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;
import com.loanmanagement.tui.screen.Area;

/** Numbered, arrow-navigable list. Up and down wrap. Enter or a digit chooses an entry; 0 is the tenth. */
public final class MenuList {
    private static final String MARKER = "❯ ";
    private static final String BLANK = "  ";

    private final List<String> labels;
    private int selected;

    public MenuList(List<String> labels) {
        this.labels = List.copyOf(labels);
    }

    public int selected() {
        return selected;
    }

    /** Returns the chosen index when the key picks an entry, otherwise empty. */
    public OptionalInt handle(KeyStroke key) {
        int count = labels.size();
        if (count == 0) {
            return OptionalInt.empty();
        }
        if (key.getKeyType() == KeyType.ArrowUp) {
            selected = (selected - 1 + count) % count;
        } else if (key.getKeyType() == KeyType.ArrowDown) {
            selected = (selected + 1) % count;
        } else if (key.getKeyType() == KeyType.Enter) {
            return OptionalInt.of(selected);
        } else if (key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.isDigit(key.getCharacter())) {
            // 1-9 pick items 1-9; 0 picks the tenth item, so every item stays reachable by digit.
            int choice = key.getCharacter() == '0' ? 9 : key.getCharacter() - '1';
            if (choice >= 0 && choice < count) {
                selected = choice;
                return OptionalInt.of(choice);
            }
        }
        return OptionalInt.empty();
    }

    public void render(TerminalRenderer renderer, Area area) {
        for (int i = 0; i < labels.size() && i < area.height(); i++) {
            boolean active = i == selected;
            String line = (active ? MARKER : BLANK) + (i + 1) + ". " + labels.get(i);
            renderer.text(area.column(), area.row() + i,
                    TextWidth.fit(line, area.width()), active ? Tone.ACCENT : Tone.PLAIN);
        }
    }
}
