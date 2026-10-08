package com.loanmanagement.testsupport;

import java.util.ArrayDeque;
import java.util.Deque;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.tui.screen.Navigation;
import com.loanmanagement.tui.screen.Screen;

/** Stack driver that applies navigation the way TuiApplication does, without a terminal. */
public final class ScreenDriver {
    private final Deque<Screen> stack = new ArrayDeque<>();

    public ScreenDriver(Screen root) {
        stack.push(root);
    }

    public Screen top() {
        return stack.peek();
    }

    public int depth() {
        return stack.size();
    }

    public void press(KeyType type) {
        apply(top().onKey(new KeyStroke(type)));
    }

    public void type(String text) {
        for (char c : text.toCharArray()) {
            apply(top().onKey(new KeyStroke(c, false, false)));
        }
    }

    public void enter(String text) {
        type(text);
        press(KeyType.Enter);
    }

    private void apply(Navigation navigation) {
        if (navigation instanceof Navigation.Push push) {
            stack.push(push.screen());
        } else if (navigation instanceof Navigation.Pop) {
            stack.pop();
        } else if (navigation instanceof Navigation.Replace replace) {
            stack.pop();
            stack.push(replace.screen());
        } else if (navigation instanceof Navigation.Quit) {
            stack.clear();
        }
    }
}
