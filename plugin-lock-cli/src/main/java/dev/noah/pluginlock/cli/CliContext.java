package dev.noah.pluginlock.cli;

import dev.noah.pluginlock.cli.io.ProgressBar;
import dev.noah.pluginlock.cli.io.Terminal;
import dev.noah.pluginlock.cli.output.CliOutput;
import dev.noah.pluginlock.cli.selection.PluginSelectionController;
import dev.noah.pluginlock.cli.selection.ServerSelectionController;
import dev.noah.pluginlock.core.DownloadProgress;
import dev.noah.pluginlock.core.catalog.PluginCatalog;
import dev.noah.pluginlock.core.doctor.DoctorService;
import dev.noah.pluginlock.core.project.ProjectPaths;
import dev.noah.pluginlock.core.server.ServerDownloads;
import dev.noah.pluginlock.core.workflow.InstallWorkflow;
import dev.noah.pluginlock.core.workflow.ProjectService;
import dev.noah.pluginlock.core.workflow.RemoveWorkflow;
import dev.noah.pluginlock.core.workflow.UpdateWorkflow;

public final class CliContext {
    private final ProjectPaths paths;
    private final ServerDownloads serverDownloads;
    private final PluginCatalog pluginCatalog;
    private final ProjectService projectService;
    private final Terminal terminal;
    private final CliOutput output;
    private final boolean json;

    CliContext(ProjectPaths paths,
               ServerDownloads serverDownloads,
               PluginCatalog pluginCatalog,
               ProjectService projectService,
               Terminal terminal,
               CliOutput output,
               boolean json) {
        this.paths = paths;
        this.serverDownloads = serverDownloads;
        this.pluginCatalog = pluginCatalog;
        this.projectService = projectService;
        this.terminal = terminal;
        this.output = output;
        this.json = json;
    }

    public ProjectPaths paths() {
        return paths;
    }

    public ServerDownloads serverDownloads() {
        return serverDownloads;
    }

    public PluginCatalog pluginCatalog() {
        return pluginCatalog;
    }

    public ProjectService projectService() {
        return projectService;
    }

    public Terminal terminal() {
        return terminal;
    }

    public CliOutput output() {
        return output;
    }

    public PluginSelectionController pluginSelection() {
        return new PluginSelectionController(pluginCatalog, terminal, output);
    }

    public ServerSelectionController serverSelection() {
        return new ServerSelectionController(terminal);
    }

    public InstallWorkflow installWorkflow() {
        return new InstallWorkflow(projectService);
    }

    public UpdateWorkflow updateWorkflow() {
        return new UpdateWorkflow(projectService);
    }

    public RemoveWorkflow removeWorkflow() {
        return new RemoveWorkflow(projectService);
    }

    public DoctorService doctorService() {
        return new DoctorService();
    }

    public DownloadProgress downloadProgress() {
        return json ? DownloadProgress.NONE : new ProgressBar(terminal);
    }
}
