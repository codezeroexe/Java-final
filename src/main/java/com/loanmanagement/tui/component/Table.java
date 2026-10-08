package com.loanmanagement.tui.component;

import java.util.List;
import java.util.OptionalInt;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.render.TextWidth;
import com.loanmanagement.tui.screen.Area;

/**
 * Fixed-width columns with a header, a selected-row marker, and scrolling. Each cell is fitted to
 * its column width, so mixed-width text (currency symbols, status dots) cannot push later columns.
 */
public final class Table {
    public static final String SELECTED = "▸ ";
    private static final String GUTTER = "  ";
    private static final String UNSELECTED = "  ";

    /** Text plus the color it is drawn in. */
    public record Cell(String text, Tone tone) {
        public static Cell plain(String text) {
            return new Cell(text, Tone.PLAIN);
        }
    }

    private final List<String> headers;
    private final int[] widths;
    private final List<List<Cell>> rows;
    private int selected;
    private int offset;

    public Table(List<String> headers, int[] widths, List<List<Cell>> rows) {
        if (headers.size() != widths.length) {
            throw new IllegalArgumentException("Each header needs a width");
        }
        this.headers = List.copyOf(headers);
        this.widths = widths.clone();
        this.rows = List.copyOf(rows);
    }

    public int selected() {
        return selected;
    }

    /** Up and down move and clamp at the ends. Enter chooses the selected row. */
    public OptionalInt handle(KeyStroke key) {
        if (rows.isEmpty()) {
            return OptionalInt.empty();
        }
        if (key.getKeyType() == KeyType.ArrowUp) {
            selected = Math.max(0, selected - 1);
        } else if (key.getKeyType() == KeyType.ArrowDown) {
            selected = Math.min(rows.size() - 1, selected + 1);
        } else if (key.getKeyType() == KeyType.Enter) {
            return OptionalInt.of(selected);
        }
        return OptionalInt.empty();
    }

    public void render(TerminalRenderer renderer, Area area) {
        if (area.height() < 1) {
            return;
        }
        drawRow(renderer, area, 0, headers, Tone.MUTED, UNSELECTED);
        if (area.height() >= 2) {
            renderer.text(area.column(), area.row() + 1,
                    TextWidth.fit("─".repeat(totalWidth()), area.width()), Tone.MUTED);
        }
        int visible = area.height() - 2;
        keepSelectedVisible(visible);
        for (int i = 0; i < visible && offset + i < rows.size(); i++) {
            int index = offset + i;
            boolean active = index == selected;
            List<Cell> cells = rows.get(index);
            drawCells(renderer, area, 2 + i, cells, active ? SELECTED : UNSELECTED);
        }
    }

    private void keepSelectedVisible(int visible) {
        if (visible <= 0) {
            return;
        }
        if (selected < offset) {
            offset = selected;
        } else if (selected >= offset + visible) {
            offset = selected - visible + 1;
        }
    }

    private void drawRow(TerminalRenderer renderer, Area area, int rowOffset,
            List<String> texts, Tone tone, String marker) {
        List<Cell> cells = texts.stream().map(text -> new Cell(text, tone)).toList();
        drawCells(renderer, area, rowOffset, cells, marker);
    }

    private void drawCells(TerminalRenderer renderer, Area area, int rowOffset,
            List<Cell> cells, String marker) {
        int y = area.row() + rowOffset;
        renderer.text(area.column(), y, TextWidth.fit(marker, marker.length()), Tone.ACCENT);
        int x = area.column() + marker.length();
        for (int c = 0; c < cells.size(); c++) {
            if (x - area.column() >= area.width()) {
                break;
            }
            Cell cell = cells.get(c);
            renderer.text(x, y, TextWidth.fit(cell.text(), widths[c]), cell.tone());
            x += widths[c] + GUTTER.length();
        }
    }

    private int totalWidth() {
        int total = SELECTED.length();
        for (int w : widths) {
            total += w;
        }
        return total + GUTTER.length() * (widths.length - 1);
    }
}
