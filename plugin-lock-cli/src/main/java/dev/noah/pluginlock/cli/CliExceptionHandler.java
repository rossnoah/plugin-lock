package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.core.provider.PluginNotFoundException;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

import java.io.IOException;

final class CliExceptionHandler implements CommandLine.IExecutionExceptionHandler {
    private static final int INTERRUPTED_EXIT_CODE = 130;

    @Override
    public int handleExecutionException(Exception exception, CommandLine commandLine, ParseResult parseResult) {
        PluginLockCli root = rootCli(commandLine);
        String command = commandLine.getCommandName();
        if (isInterrupted(exception)) {
            Thread.currentThread().interrupt();
            root.context().output().error(command, "Operation cancelled");
            return INTERRUPTED_EXIT_CODE;
        }

        root.context().output().error(command, friendlyMessage(exception));
        return commandLine.getCommandSpec().exitCodeOnExecutionException();
    }

    private static PluginLockCli rootCli(CommandLine commandLine) {
        CommandLine current = commandLine;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return (PluginLockCli) current.getCommandSpec().userObject();
    }

    private static String friendlyMessage(Exception exception) {
        if (exception instanceof PluginNotFoundException
                || exception instanceof IllegalArgumentException
                || exception instanceof IllegalStateException
                || exception instanceof IOException) {
            return messageOrFallback(exception);
        }
        return messageOrFallback(exception);
    }

    private static String messageOrFallback(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static boolean isInterrupted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
