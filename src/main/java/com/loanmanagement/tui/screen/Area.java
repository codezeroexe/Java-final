package com.loanmanagement.tui.screen;

/** A rectangle of cells a screen or component draws into. */
public record Area(int column, int row, int width, int height) {

    public Area {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Area size must not be negative");
        }
    }

    /** Area inside a one-cell margin on every side. */
    public Area inset(int margin) {
        return new Area(column + margin, row + margin,
                Math.max(0, width - 2 * margin), Math.max(0, height - 2 * margin));
    }

    /** Sub-area starting at a row offset within this area, with the given height. */
    public Area rows(int offset, int height) {
        return new Area(column, row + offset, width, Math.max(0, Math.min(height, this.height - offset)));
    }
}
