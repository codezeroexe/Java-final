package com.loanmanagement.tui.screen;

import java.util.List;

import com.googlecode.lanterna.input.KeyStroke;
import com.loanmanagement.tui.component.StatusBar;
import com.loanmanagement.tui.render.TerminalRenderer;

/** One full-screen view. Draws into an area and reacts to keys. Services are called from here, never SQL. */
public interface Screen {

    void render(TerminalRenderer renderer, Area area);

    Navigation onKey(KeyStroke key);

    /** Keys this screen answers, shown by the F1 help overlay. */
    default List<StatusBar.Hint> keyHelp() {
        return List.of(new StatusBar.Hint("Esc", "back"), new StatusBar.Hint("Ctrl-C", "exit"));
    }
}
