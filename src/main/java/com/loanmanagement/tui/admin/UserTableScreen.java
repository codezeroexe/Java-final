package com.loanmanagement.tui.admin;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.tui.component.StatusBar;
import com.loanmanagement.tui.component.Table;
import com.loanmanagement.tui.component.Table.Cell;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.screen.Area;
import com.loanmanagement.tui.screen.Navigation;
import com.loanmanagement.tui.screen.Screen;

/** Users as a table. Enter opens the selected user through {@code onSelect}. */
public final class UserTableScreen implements Screen {
    private final String title;
    private final List<User> users;
    private final Function<User, Navigation> onSelect;
    private final Table table;

    public UserTableScreen(String title, List<User> users, Function<User, Navigation> onSelect) {
        this.title = title;
        this.users = List.copyOf(users);
        this.onSelect = onSelect;
        this.table = new Table(List.of("ID", "Name", "Email", "Status"),
                new int[] {6, 20, 26, 10},
                users.stream().map(UserTableScreen::row).toList());
    }

    private static List<Cell> row(User user) {
        Cell status = user.status() == AccountStatus.ACTIVE
                ? new Cell("Active", Tone.SUCCESS)
                : new Cell("Inactive", Tone.MUTED);
        return List.of(Cell.plain(String.valueOf(user.id())),
                Cell.plain(user.firstName() + " " + user.lastName()),
                Cell.plain(user.email()),
                status);
    }

    @Override
    public void render(TerminalRenderer renderer, Area area) {
        renderer.text(area.column(), area.row(), title, Tone.WARNING);
        int hintRow = area.row() + area.height() - 1;
        table.render(renderer, new Area(area.column(), area.row() + 2, area.width(),
                Math.max(0, hintRow - area.row() - 3)));
        StatusBar.hints(renderer, new Area(area.column(), hintRow, area.width(), 1), List.of(
                new StatusBar.Hint("↑↓", "move"),
                new StatusBar.Hint("Enter", "open"),
                new StatusBar.Hint("Esc", "back")));
    }

    @Override
    public Navigation onKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            return new Navigation.Pop();
        }
        OptionalInt chosen = table.handle(key);
        return chosen.isPresent() ? onSelect.apply(users.get(chosen.getAsInt())) : Navigation.STAY;
    }
}
