package com.loanmanagement.tui.screen;

/** What a screen asks the application to do after handling a key. */
public sealed interface Navigation {

    Navigation STAY = new Stay();

    record Stay() implements Navigation {
    }

    record Push(Screen screen) implements Navigation {
    }

    record Pop() implements Navigation {
    }

    record Replace(Screen screen) implements Navigation {
    }

    record Quit() implements Navigation {
    }
}
