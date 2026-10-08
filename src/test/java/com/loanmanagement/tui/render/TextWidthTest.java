package com.loanmanagement.tui.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextWidthTest {

    @Test
    void singleCellSymbolsCountOneColumnEach() {
        assertEquals(9, TextWidth.width("₹5,00,000"));
        assertEquals(1, TextWidth.width("●"));
        assertEquals(1, TextWidth.width("✻"));
        assertEquals(1, TextWidth.width("❯"));
    }

    @Test
    void cjkCountsTwoColumnsEach() {
        assertEquals(4, TextWidth.width("漢字"));
    }

    @Test
    void decomposedAccentMeasuresSameAsComposed() {
        String decomposed = "é";
        assertEquals(1, TextWidth.width(decomposed));
        assertEquals("é", TextWidth.normalize(decomposed));
    }

    @Test
    void clipAddsEllipsisAndStaysWithinColumns() {
        String clipped = TextWidth.clip("Approved and pending review", 10);
        assertTrue(TextWidth.width(clipped) <= 10);
        assertTrue(clipped.endsWith(TextWidth.ELLIPSIS));
    }

    @Test
    void clipNeverSplitsWideCharacter() {
        assertEquals("漢…", TextWidth.clip("漢字漢字", 3));
    }

    @Test
    void clipLeavesShortTextAlone() {
        assertEquals("Home", TextWidth.clip("Home", 10));
    }

    @Test
    void fitPadsToExactWidth() {
        assertEquals("abc   ", TextWidth.fit("abc", 6));
        assertEquals(6, TextWidth.width(TextWidth.fit("abc", 6)));
    }

    @Test
    void fitClipsToExactWidth() {
        assertEquals(6, TextWidth.width(TextWidth.fit("a very long label", 6)));
    }

    @Test
    void maskHidesContentButKeepsLength() {
        assertEquals("***", TextWidth.mask("abc"));
        assertEquals("", TextWidth.mask(""));
    }
}
