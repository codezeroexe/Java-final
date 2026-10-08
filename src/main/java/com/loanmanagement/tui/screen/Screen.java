package com.loanmanagement.tui.screen;

import com.googlecode.lanterna.input.KeyStroke;
import com.loanmanagement.tui.render.TerminalRenderer;

/** One full-screen view. Draws into an area and reacts to keys. Services are called from here, never SQL. */
public interface Screen {

    void render(TerminalRenderer renderer, Area area);

    Navigation onKey(KeyStroke key);
}
