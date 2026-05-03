package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.cli.PluginLockCli;
import dev.noah.pluginlock.core.doctor.DoctorReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "doctor", mixinStandardHelpOptions = true, description = "Check project files, locked artifacts, and hashes.")
public final class DoctorCommand implements Callable<Integer> {
    @ParentCommand
    PluginLockCli parent;

    @Option(names = "--plugins-dir", defaultValue = "plugins", description = "Destination plugins directory.")
    Path pluginsDir;

    @Override
    public Integer call() throws Exception {
        CliContext context = parent.context();
        DoctorReport report = context.doctorService().check(context.paths(), context.paths().pluginsDir(pluginsDir));
        context.output().doctor(report);
        return report.hasErrors() ? 1 : 0;
    }
}
