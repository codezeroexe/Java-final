package com.loanmanagement.tui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.loanmanagement.tui.render.TerminalRenderer.Tone;

class TerminalRendererTest {

    private TerminalRenderer renderer;

    @BeforeEach
    void open() throws IOException {
        renderer = TerminalRenderer.virtual(20, 6);
    }

    @AfterEach
    void close() {
        renderer.close();
    }

    @Test
    void writesTextAtCellPosition() throws IOException {
        renderer.text(2, 1, "Hello", Tone.PLAIN);
        renderer.flush();
        assertEquals("H", renderer.cell(2, 1));
        assertEquals("o", renderer.cell(6, 1));
    }

    @Test
    void clipsTextAtRightEdge() throws IOException {
        renderer.text(15, 0, "abcdefgh", Tone.PLAIN);
        renderer.flush();
        assertEquals("…", renderer.cell(19, 0));
    }

    @Test
    void ignoresPositionsOutsideScreen() throws IOException {
        assertDoesNotThrow(() -> renderer.text(-1, 0, "x", Tone.PLAIN));
        assertDoesNotThrow(() -> renderer.text(0, 99, "x", Tone.PLAIN));
        renderer.flush();
    }

    @Test
    void drawsRoundedBoxWithTitle() throws IOException {
        renderer.box(0, 0, 10, 4, "Menu", Tone.PLAIN);
        renderer.flush();
        assertEquals("╭", renderer.cell(0, 0));
        assertEquals("M", renderer.cell(3, 0));
        assertEquals("╮", renderer.cell(9, 0));
        assertEquals("│", renderer.cell(0, 1));
        assertEquals("│", renderer.cell(9, 2));
        assertEquals("╰", renderer.cell(0, 3));
        assertEquals("╯", renderer.cell(9, 3));
    }

    @Test
    void wideTitleKeepsRightCornerAligned() throws IOException {
        renderer.box(0, 0, 12, 3, "漢字", Tone.PLAIN);
        renderer.flush();
        assertEquals("╮", renderer.cell(11, 0));
        assertEquals("│", renderer.cell(11, 1));
        assertEquals("╯", renderer.cell(11, 2));
    }

    @Test
    void wideContentInsideBoxKeepsBorderAligned() throws IOException {
        renderer.box(0, 0, 12, 3, "", Tone.PLAIN);
        renderer.text(1, 1, TextWidth.fit("₹5,00,000 ● Approved", 10), Tone.PLAIN);
        renderer.flush();
        assertEquals("│", renderer.cell(11, 1));
    }

    @Test
    void tooSmallBoxIsSkipped() throws IOException {
        renderer.box(0, 0, 3, 3, "x", Tone.PLAIN);
        renderer.flush();
        assertEquals(" ", renderer.cell(0, 0));
    }

    @Test
    void closeIsIdempotent() {
        renderer.close();
        assertDoesNotThrow(renderer::close);
    }
}
