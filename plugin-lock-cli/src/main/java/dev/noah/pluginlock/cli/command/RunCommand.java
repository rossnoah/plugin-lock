package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.core.PluginLockFiles;
import dev.noah.pluginlock.core.model.LockedServer;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginManifest;
import dev.noah.pluginlock.core.run.ServerRunCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Command(name = "run", mixinStandardHelpOptions = true, description = "Start the locked server jar with optimized JVM flags.")
public final class RunCommand implements Callable<Integer> {
    private static final String DEFAULT_SERVER_MEMORY = "2G";
    private static final long SERVER_INTERRUPT_GRACE_SECONDS = 30;
    private static final long SERVER_DESTROY_GRACE_SECONDS = 2;

    @ParentCommand
    PluginLockCli parent;

    @Option(names = {"-m", "--memory"}, description = "Heap size for -Xms and -Xmx, for example 2G, 2048M, or 4096M.")
    String memory;

    @Option(names = "--jar", description = "Server jar to run. Defaults to the locked server jar.")
    Path jar;

    @Option(names = "--java", defaultValue = "java", description = "Java executable.")
    String javaExecutable;

    @Option(names = "--dry-run", description = "Print the Java command without starting the server.")
    boolean dryRun;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        Path serverJar = resolveServerJar(context);
        String heap = resolveMemory(context);
        List<String> command = ServerRunCommand.build(javaExecutable, heap, serverJar);
        if (dryRun) {
            context.terminal().println(ServerRunCommand.format(command));
            return 0;
        }
        Process process = new ProcessBuilder(command)
                .directory(context.paths().root().toFile())
                .inheritIO()
                .start();
        try {
            return process.waitFor();
        } catch (InterruptedException exception) {
            if (process.waitFor(SERVER_INTERRUPT_GRACE_SECONDS, TimeUnit.SECONDS)) {
                return process.exitValue();
            }
            process.destroy();
            try {
                if (!process.waitFor(SERVER_DESTROY_GRACE_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException forceException) {
                process.destroyForcibly();
                forceException.addSuppressed(exception);
                throw forceException;
            }
            throw exception;
        }
    }

    private String resolveMemory(CliContext context) throws Exception {
        if (memory != null && !memory.isBlank()) {
            return ServerRunCommand.normalizeMemory(memory);
        }
        PluginManifest manifest = Files.exists(context.paths().manifestPath())
                ? PluginLockFiles.readManifest(context.paths().manifestPath())
                : new PluginManifest();
        if (manifest.getRunMemory() != null && !manifest.getRunMemory().isBlank()) {
            return ServerRunCommand.normalizeMemory(manifest.getRunMemory());
        }
        String answer = context.terminal().readLine("Server memory [" + DEFAULT_SERVER_MEMORY + "]: ");
        String heap = ServerRunCommand.normalizeMemory(answer.isBlank() ? DEFAULT_SERVER_MEMORY : answer);
        manifest.setRunMemory(heap);
        PluginLockFiles.writeManifest(context.paths().manifestPath(), manifest);
        return heap;
    }

    private Path resolveServerJar(CliContext context) throws Exception {
        if (jar != null) {
            return jar.isAbsolute() ? jar : context.paths().root().resolve(jar);
        }
        if (Files.notExists(context.paths().lockPath())) {
            throw new IllegalStateException("No server-lock.lock.json found. Run `pl init` first or pass --jar.");
        }
        PluginLock lock = PluginLockFiles.readLock(context.paths().lockPath());
        LockedServer server = lock.getServer();
        if (server == null || server.getFileName() == null || server.getFileName().isBlank()) {
            throw new IllegalStateException("No locked server jar found. Run `pl init` first or pass --jar.");
        }
        return context.paths().root().resolve(server.getFileName());
    }
}
