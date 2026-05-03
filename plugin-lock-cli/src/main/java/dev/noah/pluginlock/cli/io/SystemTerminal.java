package dev.noah.pluginlock.cli.io;

import java.io.Console;

public final class SystemTerminal implements Terminal {
    @Override
    public String readLine(String prompt) {
        Console console = System.console();
        if (console != null) {
            String answer = console.readLine(prompt);
            return answer == null ? "" : answer.trim();
        }
        System.out.print(prompt);
        try {
            StringBuilder answer = new StringBuilder();
            while (true) {
                int next = System.in.read();
                if (next == -1 || next == '\n') {
                    break;
                }
                if (next != '\r') {
                    answer.append((char) next);
                }
            }
            return answer.toString().trim();
        } catch (Exception exception) {
            return "";
        }
    }

    @Override
    public void print(String value) {
        System.out.print(value);
    }

    @Override
    public void println(String value) {
        System.out.println(value);
    }

    @Override
    public void error(String value) {
        System.err.println(value);
    }
}
