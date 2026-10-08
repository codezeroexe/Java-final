package com.loanmanagement.tui.render;

import java.text.Normalizer;

import com.googlecode.lanterna.TerminalTextUtils;

/** Column-width aware text helpers. All TUI layout goes through these, never String.length(). */
public final class TextWidth {
    public static final String ELLIPSIS = "…";
    public static final char MASK = '*';

    private TextWidth() {
    }

    /** Canonical form for layout: composed characters, so combining marks do not split cells. */
    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    public static int width(String text) {
        return TerminalTextUtils.getColumnWidth(normalize(text));
    }

    /** Clips to at most {@code columns} cells, adding an ellipsis when clipped. Never splits a code point. */
    public static String clip(String text, int columns) {
        String normalized = normalize(text);
        if (columns <= 0) {
            return "";
        }
        if (width(normalized) <= columns) {
            return normalized;
        }
        int budget = columns - width(ELLIPSIS);
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = 0; i < normalized.length(); ) {
            int codePoint = normalized.codePointAt(i);
            String glyph = new String(Character.toChars(codePoint));
            int glyphWidth = width(glyph);
            if (used + glyphWidth > budget) {
                break;
            }
            out.append(glyph);
            used += glyphWidth;
            i += Character.charCount(codePoint);
        }
        return out.append(ELLIPSIS).toString();
    }

    /** Pads with spaces or clips so the result occupies exactly {@code columns} cells. */
    public static String fit(String text, int columns) {
        String clipped = clip(text, columns);
        int padding = columns - width(clipped);
        return padding > 0 ? clipped + " ".repeat(padding) : clipped;
    }

    /** Masks a secret for display. One mask character per input character, so the length is not hidden from layout. */
    public static String mask(CharSequence secret) {
        return String.valueOf(MASK).repeat(secret.length());
    }
}
