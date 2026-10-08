package com.loanmanagement.tui.component;

import java.util.List;
import java.util.Optional;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;
import com.loanmanagement.tui.screen.Area;

/**
 * Stack of fields with one focused. Up and down (or Tab) move focus. Enter moves to the next field,
 * and submits from the last one. Escape cancels. A form-level error shows under the fields.
 */
public final class Form {
    public enum Outcome {
        SUBMIT,
        CANCEL
    }

    private final List<Field> fields;
    private int focus;
    private String formError;

    public Form(List<Field> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("A form needs at least one field");
        }
        this.fields = List.copyOf(fields);
    }

    /** Shows a validation message for the whole form. Null clears it. */
    public void setFormError(String message) {
        this.formError = message;
    }

    public int focus() {
        return focus;
    }

    public Optional<Outcome> handle(KeyStroke key) {
        KeyType type = key.getKeyType();
        if (type == KeyType.Escape) {
            return Optional.of(Outcome.CANCEL);
        }
        if (type == KeyType.ArrowDown || type == KeyType.Tab) {
            focus = Math.min(fields.size() - 1, focus + 1);
            return Optional.empty();
        }
        if (type == KeyType.ArrowUp) {
            focus = Math.max(0, focus - 1);
            return Optional.empty();
        }
        if (type == KeyType.Enter) {
            if (focus == fields.size() - 1) {
                return Optional.of(Outcome.SUBMIT);
            }
            focus++;
            return Optional.empty();
        }
        if (fields.get(focus).handle(key)) {
            formError = null;
        }
        return Optional.empty();
    }

    public void render(TerminalRenderer renderer, Area area) {
        int y = area.row();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            int height = field.height();
            if (y - area.row() + height > area.height()) {
                break;
            }
            field.render(renderer, new Area(area.column(), y, area.width(), height), i == focus);
            y += height;
        }
        if (formError != null && y - area.row() < area.height()) {
            renderer.text(area.column(), y, TextWidth.fit(formError, area.width()), Tone.DANGER);
        }
    }
}
