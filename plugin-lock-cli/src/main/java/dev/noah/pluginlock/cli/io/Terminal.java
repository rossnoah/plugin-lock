package dev.noah.pluginlock.cli.io;

public interface Terminal {
    String readLine(String prompt);

    void print(String value);

    void println(String value);

    void error(String value);
}
