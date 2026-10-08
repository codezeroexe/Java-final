package com.loanmanagement.tui.component;

import java.util.Optional;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;
import com.loanmanagement.tui.screen.Area;

/**
 * Yes or no prompt in a box. A destructive dialog confirms only on its explicit key (for example
 * {@code 'd'}). Enter in that mode cancels, so a delete cannot be confirmed by Enter.
 */
public final class Dialog {
    private final String title;
    private final String message;
    private final Character explicitKey;
    private boolean confirmFocused;

    private Dialog(String title, String message, Character explicitKey, boolean confirmFocused) {
        this.title = title;
        this.message = message;
        this.explicitKey = explicitKey;
        this.confirmFocused = confirmFocused;
    }

    /** Ordinary confirm. Focus starts on Confirm; Enter confirms. */
    public static Dialog confirm(String title, String message) {
        return new Dialog(title, message, null, true);
    }

    /** Destructive confirm. Focus starts on Cancel; only the explicit key confirms. */
    public static Dialog destructive(String title, String message, char explicitKey) {
        return new Dialog(title, message, explicitKey, false);
    }

    /** Empty until the user decides: true for confirm, false for cancel. */
    public Optional<Boolean> handle(KeyStroke key) {
        KeyType type = key.getKeyType();
        if (type == KeyType.Escape) {
            return Optional.of(false);
        }
        if (type == KeyType.ArrowLeft || type == KeyType.ArrowRight || type == KeyType.Tab) {
            confirmFocused = !confirmFocused;
            return Optional.empty();
        }
        if (type == KeyType.Character && key.getCharacter() != null) {
            char typed = Character.toLowerCase(key.getCharacter());
            if (explicitKey != null && typed == Character.toLowerCase(explicitKey)) {
                return Optional.of(true);
            }
            if (explicitKey == null && typed == 'y') {
                return Optional.of(true);
            }
            if (typed == 'n') {
                return Optional.of(false);
            }
            return Optional.empty();
        }
        if (type == KeyType.Enter) {
            if (explicitKey != null) {
                return Optional.of(false);
            }
            return Optional.of(confirmFocused);
        }
        return Optional.empty();
    }

    public void render(TerminalRenderer renderer, Area area) {
        int width = Math.min(area.width(), Math.max(TextWidth.width(message) + 4, 30));
        int height = 6;
        if (area.height() < height || width < 4) {
            return;
        }
        int column = area.column() + (area.width() - width) / 2;
        int row = area.row() + (area.height() - height) / 2;
        renderer.box(column, row, width, height, title, Tone.PLAIN);
        Area inner = new Area(column + 2, row + 1, width - 4, height - 2);
        renderer.text(inner.column(), inner.row(), TextWidth.fit(message, inner.width()), Tone.PLAIN);
        String confirmLabel = explicitKey != null ? "[ Delete ]" : "[ Yes ]";
        String cancelLabel = "[ No ]";
        renderer.text(inner.column(), inner.row() + 2,
                TextWidth.fit(confirmLabel, inner.width()), confirmFocused ? Tone.ACCENT : Tone.PLAIN);
        renderer.text(inner.column() + confirmLabel.length() + 3, inner.row() + 2,
                TextWidth.fit(cancelLabel, inner.width()), confirmFocused ? Tone.PLAIN : Tone.ACCENT);
        renderer.text(inner.column(), inner.row() + 3,
                TextWidth.fit(focusHint(), inner.width()), Tone.MUTED);
    }

    private String focusHint() {
        if (explicitKey != null) {
            return "Press " + explicitKey + " to confirm   Esc cancel";
        }
        return confirmFocused ? "Enter confirm   Esc cancel" : "Enter cancel   Esc cancel";
    }
}
