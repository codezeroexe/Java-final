package com.loanmanagement.tui.screen;

import java.util.List;
import java.util.Optional;

import com.googlecode.lanterna.input.KeyStroke;
import com.loanmanagement.tui.component.Form;
import com.loanmanagement.tui.component.StatusBar;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;

/** Title, optional subtitle, a form, and key hints. Submit runs the submitter; Escape goes back. */
public final class FormScreen implements Screen {

    /** Runs when the form submits. Returns STAY to keep the form open, for example after a validation error. */
    @FunctionalInterface
    public interface Submitter {
        Navigation submit(Form form);
    }

    private final String title;
    private final String subtitle;
    private final Form form;
    private final Submitter submitter;

    public FormScreen(String title, String subtitle, Form form, Submitter submitter) {
        this.title = title;
        this.subtitle = subtitle;
        this.form = form;
        this.submitter = submitter;
    }

    public String title() {
        return title;
    }

    @Override
    public void render(TerminalRenderer renderer, Area area) {
        renderer.text(area.column(), area.row(), TextWidth.fit(title, area.width()), Tone.WARNING);
        if (subtitle != null) {
            renderer.text(area.column(), area.row() + 1, TextWidth.fit(subtitle, area.width()), Tone.MUTED);
        }
        int formRow = area.row() + 3;
        int hintRow = area.row() + area.height() - 1;
        form.render(renderer, new Area(area.column(), formRow, area.width(), Math.max(0, hintRow - formRow - 1)));
        StatusBar.hints(renderer, new Area(area.column(), hintRow, area.width(), 1), List.of(
                new StatusBar.Hint("↑↓", "move"),
                new StatusBar.Hint("Enter", "next or submit"),
                new StatusBar.Hint("Esc", "back")));
    }

    @Override
    public Navigation onKey(KeyStroke key) {
        Optional<Form.Outcome> outcome = form.handle(key);
        if (outcome.isEmpty()) {
            return Navigation.STAY;
        }
        return outcome.get() == Form.Outcome.SUBMIT ? submitter.submit(form) : new Navigation.Pop();
    }
}
