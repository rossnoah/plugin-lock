package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.cli.command.AddCommand;
import dev.noah.pluginlock.cli.command.CleanInstallCommand;
import dev.noah.pluginlock.cli.command.DoctorCommand;
import dev.noah.pluginlock.cli.command.InfoCommand;
import dev.noah.pluginlock.cli.command.InitCommand;
import dev.noah.pluginlock.cli.command.InstallCommand;
import dev.noah.pluginlock.cli.command.ListCommand;
import dev.noah.pluginlock.cli.command.LockCommand;
import dev.noah.pluginlock.cli.command.RemoveCommand;
import dev.noah.pluginlock.cli.command.RunCommand;
import dev.noah.pluginlock.cli.command.SearchCommand;
import dev.noah.pluginlock.cli.command.UpdateCommand;
import dev.noah.pluginlock.cli.io.SystemTerminal;
import dev.noah.pluginlock.cli.io.Terminal;
import dev.noah.pluginlock.cli.output.CliOutput;
import dev.noah.pluginlock.core.PluginResolver;
import dev.noah.pluginlock.core.catalog.PluginCatalog;
import dev.noah.pluginlock.core.project.ProjectLocator;
import dev.noah.pluginlock.core.project.ProjectPaths;
import dev.noah.pluginlock.core.server.ServerDownloads;
import dev.noah.pluginlock.core.workflow.ProjectService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "pl",
        mixinStandardHelpOptions = true,
        version = "plugin-lock 0.1.0",
        description = "Lock and install Minecraft server plugins.",
        subcommands = {
                InitCommand.class,
                AddCommand.class,
                LockCommand.class,
                InstallCommand.class,
                CleanInstallCommand.class,
                RemoveCommand.class,
                ListCommand.class,
                DoctorCommand.class,
                UpdateCommand.class,
                SearchCommand.class,
                InfoCommand.class,
                RunCommand.class
        }
)
public final class PluginLockCli implements Callable<Integer> {
    @Option(names = "--project-dir", defaultValue = ".", description = "Directory containing server-lock files.")
    Path projectDir;

    @Option(names = "--verbose", description = "Show detailed command output.")
    boolean verbose;

    @Option(names = "--json", description = "Emit machine-readable JSON events.")
    boolean json;

    private final ServerDownloads serverDownloads;
    private final ProjectService.LockResolver lockResolver;
    private final PluginCatalog pluginCatalog;
    private final Terminal terminal;
    private CliContext context;

    public PluginLockCli() {
        this(new ServerDownloads(HttpClient.newHttpClient()), new PluginResolver()::resolve, new PluginCatalog(), new SystemTerminal());
    }

    PluginLockCli(ServerDownloads serverDownloads) {
        this(serverDownloads, new PluginResolver()::resolve, new PluginCatalog(), new SystemTerminal());
    }

    PluginLockCli(ServerDownloads serverDownloads, ProjectService.LockResolver lockResolver) {
        this(serverDownloads, lockResolver, new PluginCatalog(), new SystemTerminal());
    }

    PluginLockCli(ServerDownloads serverDownloads, ProjectService.LockResolver lockResolver, PluginCatalog pluginCatalog, Terminal terminal) {
        this.serverDownloads = serverDownloads;
        this.lockResolver = lockResolver;
        this.pluginCatalog = pluginCatalog;
        this.terminal = terminal;
    }

    public static void main(String[] args) {
        PluginLockCli cli = new PluginLockCli();
        CancellationHandler.install(cli);
        int exitCode = commandLine(cli).execute(args);
        System.exit(exitCode);
    }

    public static CommandLine commandLine(PluginLockCli cli) {
        return new CommandLine(cli)
                .setExecutionExceptionHandler(new CliExceptionHandler());
    }

    public CliContext context() {
        if (context == null) {
            ProjectPaths paths = new ProjectPaths(ProjectLocator.detectProjectDir(projectDir));
            ProjectService projectService = new ProjectService(paths, serverDownloads, lockResolver);
            CliOutput output = new CliOutput(terminal, verbose, json);
            context = new CliContext(paths, serverDownloads, pluginCatalog, projectService, terminal, output, json);
        }
        return context;
    }

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }
}
