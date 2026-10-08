package com.loanmanagement.tui.component;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;
import com.loanmanagement.tui.screen.Area;

/**
 * Single-line text input. Secret fields show masks, never the value. Editing clears any error,
 * so a stale validation message does not survive a correction.
 */
public final class TextField implements Field {
    private final String label;
    private final boolean secret;
    private final int maxLength;
    private final StringBuilder value = new StringBuilder();
    private String error;

    public TextField(String label, boolean secret, int maxLength) {
        this.label = label;
        this.secret = secret;
        this.maxLength = maxLength;
    }

    public String value() {
        return value.toString();
    }

    public void clear() {
        value.setLength(0);
        error = null;
    }

    /** Sets a validation message shown under the field. Null clears it. */
    public void setError(String message) {
        this.error = message;
    }

    @Override
    public int height() {
        return error == null ? 1 : 2;
    }

    @Override
    public boolean handle(KeyStroke key) {
        if (key.getKeyType() == KeyType.Backspace) {
            if (!value.isEmpty()) {
                value.setLength(value.length() - Character.charCount(
                        value.codePointBefore(value.length())));
            }
            error = null;
            return true;
        }
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            if (value.codePointCount(0, value.length()) < maxLength) {
                value.append(key.getCharacter());
            }
            error = null;
            return true;
        }
        return false;
    }

    @Override
    public void render(TerminalRenderer renderer, Area area, boolean focused) {
        String shown = secret ? TextWidth.mask(value) : value.toString();
        renderer.text(area.column(), area.row(),
                TextWidth.fit(label + ": " + shown, area.width()), focused ? Tone.ACCENT : Tone.PLAIN);
        if (error != null && area.height() > 1) {
            renderer.text(area.column(), area.row() + 1,
                    TextWidth.fit(error, area.width()), Tone.DANGER);
        }
    }

    /** Render without focus styling. Kept for callers that draw a single field. */
    public void render(TerminalRenderer renderer, Area area) {
        render(renderer, area, false);
    }
}
