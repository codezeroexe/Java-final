package com.loanmanagement.tui.component;

import com.googlecode.lanterna.input.KeyStroke;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.screen.Area;

/** One input row in a {@link Form}. */
public interface Field {

    /** Rows this field needs, including any inline error. */
    int height();

    void render(TerminalRenderer renderer, Area area, boolean focused);

    /** Returns true when the key was used by this field. */
    boolean handle(KeyStroke key);
}
