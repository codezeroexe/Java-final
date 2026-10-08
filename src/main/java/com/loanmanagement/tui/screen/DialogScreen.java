package com.loanmanagement.tui.screen;

import java.util.Optional;
import java.util.function.Function;

import com.googlecode.lanterna.input.KeyStroke;
import com.loanmanagement.tui.component.Dialog;
import com.loanmanagement.tui.render.TerminalRenderer;

/** Hosts a {@link Dialog}. The decision is passed on; Escape or a cancel key comes back as false. */
public final class DialogScreen implements Screen {
    private final Dialog dialog;
    private final Function<Boolean, Navigation> onDecision;

    public DialogScreen(Dialog dialog, Function<Boolean, Navigation> onDecision) {
        this.dialog = dialog;
        this.onDecision = onDecision;
    }

    @Override
    public void render(TerminalRenderer renderer, Area area) {
        dialog.render(renderer, area);
    }

    @Override
    public Navigation onKey(KeyStroke key) {
        Optional<Boolean> decision = dialog.handle(key);
        return decision.isPresent() ? onDecision.apply(decision.get()) : Navigation.STAY;
    }
}
