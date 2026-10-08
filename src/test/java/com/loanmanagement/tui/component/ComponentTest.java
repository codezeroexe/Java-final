package com.loanmanagement.tui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.screen.Area;

class ComponentTest {

    private static KeyStroke key(KeyType type) {
        return new KeyStroke(type);
    }

    private static KeyStroke typed(char c) {
        return new KeyStroke(c, false, false);
    }

    private TerminalRenderer renderer;

    @BeforeEach
    void open() throws IOException {
        renderer = TerminalRenderer.virtual(40, 10);
    }

    @AfterEach
    void close() {
        renderer.close();
    }

    @Test
    void menuWrapsAndEnterChoosesSelected() {
        MenuList menu = new MenuList(List.of("Register", "Login", "Exit"));
        assertEquals(OptionalInt.empty(), menu.handle(key(KeyType.ArrowUp)));
        assertEquals(2, menu.selected());
        assertEquals(OptionalInt.of(2), menu.handle(key(KeyType.Enter)));
        menu.handle(key(KeyType.ArrowDown));
        assertEquals(0, menu.selected());
    }

    @Test
    void menuDigitChoosesImmediatelyAndIgnoresOutOfRange() {
        MenuList menu = new MenuList(List.of("Register", "Login"));
        assertEquals(OptionalInt.of(1), menu.handle(typed('2')));
        assertEquals(OptionalInt.empty(), menu.handle(typed('9')));
        assertEquals(1, menu.selected());
    }

    @Test
    void textFieldMasksSecretAndKeepsValue() throws IOException {
        TextField password = new TextField("Password", true, 20);
        for (char c : "Secret1!".toCharArray()) {
            password.handle(typed(c));
        }
        password.render(renderer, new Area(0, 0, 30, 1));
        renderer.flush();
        assertEquals("*", renderer.cell(10, 0));
        assertEquals("Secret1!", password.value());
    }

    @Test
    void textFieldBackspaceAndLimit() {
        TextField field = new TextField("User", false, 3);
        for (char c : "abcdef".toCharArray()) {
            field.handle(typed(c));
        }
        assertEquals("abc", field.value());
        field.handle(key(KeyType.Backspace));
        assertEquals("ab", field.value());
    }

    @Test
    void textFieldEditClearsValidationError() throws IOException {
        TextField field = new TextField("Mobile", false, 10);
        field.setError("Mobile must be 10 digits");
        field.render(renderer, new Area(0, 0, 30, 2));
        renderer.flush();
        assertEquals("M", renderer.cell(0, 1));

        field.handle(typed('9'));
        renderer.clear();
        field.render(renderer, new Area(0, 0, 30, 2));
        renderer.flush();
        assertEquals(" ", renderer.cell(0, 1));
    }

    @Test
    void tableClampsSelectionAndChoosesRow() {
        Table table = new Table(List.of("#", "Status"), new int[] {4, 10}, List.of(
                List.of(Table.Cell.plain("1"), Table.Cell.plain("Pending")),
                List.of(Table.Cell.plain("2"), Table.Cell.plain("Approved"))));
        table.handle(key(KeyType.ArrowUp));
        assertEquals(0, table.selected());
        table.handle(key(KeyType.ArrowDown));
        table.handle(key(KeyType.ArrowDown));
        assertEquals(1, table.selected());
        assertEquals(OptionalInt.of(1), table.handle(key(KeyType.Enter)));
    }

    @Test
    void tableKeepsColumnsAlignedWithMixedWidthText() throws IOException {
        Table table = new Table(List.of("Amount", "Status"), new int[] {10, 10}, List.of(
                List.of(new Table.Cell("₹5,00,000", Tone.PLAIN), new Table.Cell("● Approved", Tone.SUCCESS)),
                List.of(new Table.Cell("₹1,00,000", Tone.PLAIN), new Table.Cell("● Rejected", Tone.DANGER))));
        table.render(renderer, new Area(0, 0, 40, 6));
        renderer.flush();
        // Marker (2) + first column (10) + gutter (2) puts the status column at x = 14 on every row.
        assertEquals("S", renderer.cell(14, 0));
        assertEquals("●", renderer.cell(14, 2));
        assertEquals("●", renderer.cell(14, 3));
    }

    @Test
    void statusBarJoinsHintsWithSeparators() throws IOException {
        StatusBar.hints(renderer, new Area(0, 0, 40, 1), List.of(
                new StatusBar.Hint("Enter", "select"), new StatusBar.Hint("Esc", "back")));
        renderer.flush();
        assertEquals("E", renderer.cell(0, 0));
        // "Enter select" is 12 cells, then a 3-cell separator, so "Esc" starts at column 15.
        assertEquals("E", renderer.cell(15, 0));
    }

    @Test
    void confirmDialogEnterConfirmsByDefault() {
        Dialog dialog = Dialog.confirm("Activate", "Activate this user?");
        assertEquals(Optional.of(true), dialog.handle(key(KeyType.Enter)));
    }

    @Test
    void confirmDialogTabMovesFocusToNo() {
        Dialog dialog = Dialog.confirm("Activate", "Activate this user?");
        assertEquals(Optional.empty(), dialog.handle(key(KeyType.Tab)));
        assertEquals(Optional.of(false), dialog.handle(key(KeyType.Enter)));
    }

    @Test
    void destructiveDialogEnterNeverConfirms() {
        Dialog dialog = Dialog.destructive("Delete user", "Anonymize this user?", 'd');
        assertEquals(Optional.of(false), dialog.handle(key(KeyType.Enter)));
        dialog.handle(key(KeyType.Tab));
        assertEquals(Optional.of(false), dialog.handle(key(KeyType.Enter)));
    }

    @Test
    void destructiveDialogConfirmsOnlyOnExplicitKey() {
        Dialog dialog = Dialog.destructive("Delete user", "Anonymize this user?", 'd');
        assertEquals(Optional.empty(), dialog.handle(typed('x')));
        assertEquals(Optional.of(true), dialog.handle(typed('d')));
    }

    @Test
    void escapeCancelsAnyDialog() {
        assertEquals(Optional.of(false), Dialog.confirm("T", "M").handle(key(KeyType.Escape)));
        assertEquals(Optional.of(false), Dialog.destructive("T", "M", 'd').handle(key(KeyType.Escape)));
        assertTrue(Dialog.confirm("T", "M").handle(typed('y')).isPresent());
    }
}
