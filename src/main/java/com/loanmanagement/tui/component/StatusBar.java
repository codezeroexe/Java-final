package com.loanmanagement.tui.component;

import java.util.List;

import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;
import com.loanmanagement.tui.screen.Area;

/** One muted row of key hints, or of session facts such as user, role, and CIBIL. */
public final class StatusBar {
    public record Hint(String key, String action) {
    }

    private static final String SEPARATOR = "   ";

    private StatusBar() {
    }

    /** Session facts joined with a middle dot, for the status line. */
    public static void facts(TerminalRenderer renderer, Area area, List<String> facts) {
        renderer.text(area.column(), area.row(),
                TextWidth.fit(String.join("  ·  ", facts), area.width()), Tone.MUTED);
    }

    /** Key hints such as "Enter select   Esc back". */
    public static void hints(TerminalRenderer renderer, Area area, List<Hint> hints) {
        StringBuilder line = new StringBuilder();
        for (Hint hint : hints) {
            if (!line.isEmpty()) {
                line.append(SEPARATOR);
            }
            line.append(hint.key()).append(' ').append(hint.action());
        }
        renderer.text(area.column(), area.row(), TextWidth.fit(line.toString(), area.width()), Tone.MUTED);
    }
}
