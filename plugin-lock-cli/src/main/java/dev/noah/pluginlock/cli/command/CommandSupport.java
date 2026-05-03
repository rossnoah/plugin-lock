package dev.noah.pluginlock.cli.command;

import dev.noah.pluginlock.cli.CliContext;
import dev.noah.pluginlock.core.PluginInstaller.InstallResult;
import dev.noah.pluginlock.core.manifest.ManifestEditor;
import dev.noah.pluginlock.core.manifest.RequestChange;
import dev.noah.pluginlock.core.model.LockedPlugin;
import dev.noah.pluginlock.core.model.PluginLock;
import dev.noah.pluginlock.core.model.PluginRequest;
import dev.noah.pluginlock.core.workflow.InstallWorkflow;
import dev.noah.pluginlock.core.workflow.RemoveWorkflow;
import dev.noah.pluginlock.core.workflow.UpdateWorkflow;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CommandSupport {
    private CommandSupport() {
    }

    static void emitCompatibilityWarnings(CliContext context, PluginLock lock) {
        lock.getPlugins().stream()
                .map(LockedPlugin::getCompatibilityWarning)
                .filter(warning -> warning != null && !warning.isBlank())
                .forEach(context.output()::warning);
    }

    static String addMessage(RequestChange change) {
        if (change.added()) {
            return "Added " + change.request().getId();
        }
        if (change.duplicateCleanup() && !change.providerSwitch()) {
            return "Cleaned up duplicate " + change.request().getId();
        }
        if (change.providerSwitch()) {
            return "Switched " + change.request().getId() + " to " + ManifestEditor.requestId(change.request());
        }
        return "Updated " + change.request().getId();
    }

    static String installMessage(InstallWorkflow.InstallWorkflowResult result) {
        PluginLock lock = result.lock();
        List<PluginRequest> requested = result.requested();
        InstallResult installResult = result.installResult();
        int lockedCount = lock.getPlugins().size();
        String prefix = installChangePrefix(result.requestChanges());
        if (requested.isEmpty()) {
            if (lockedCount == 0) {
                return "No locked plugins to install";
            }
            if (installResult.installedCount() == 0) {
                return "All " + lockedCount + " locked plugin(s) already installed";
            }
            if (installResult.alreadyInstalledCount() == 0) {
                return "Installed " + installResult.installedCount() + " locked plugin(s)";
            }
            return "Installed " + installResult.installedCount() + " locked plugin(s); "
                    + installResult.alreadyInstalledCount() + " already installed";
        }
        List<String> requestedFiles = lockedFilesForRequests(lock, requested);
        int requestedInstalledCount = countMatching(installResult.installedFiles(), requestedFiles);
        int requestedAlreadyInstalledCount = countMatching(installResult.alreadyInstalledFiles(), requestedFiles);
        if (requestedInstalledCount == 0 && requestedAlreadyInstalledCount > 0) {
            return prefix + "Requested plugin(s) already installed; " + lockedCount + " locked plugin(s) checked";
        }
        if (requestedAlreadyInstalledCount == 0) {
            return prefix + "Installed " + requestedInstalledCount + " requested plugin(s); "
                    + lockedCount + " locked plugin(s) checked";
        }
        return prefix + "Installed " + requestedInstalledCount + " requested plugin(s); "
                + requestedAlreadyInstalledCount + " already installed; "
                + lockedCount + " locked plugin(s) checked";
    }

    static Map<String, ?> installDetails(InstallWorkflow.InstallWorkflowResult result) {
        PluginLock lock = result.lock();
        List<PluginRequest> requested = result.requested();
        InstallResult installResult = result.installResult();
        List<String> requestedFiles = lockedFilesForRequests(lock, requested);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("count", requested.isEmpty() ? lock.getPlugins().size() : requested.size());
        details.put("lockedCount", lock.getPlugins().size());
        details.put("requestedCount", requested.size());
        details.put("installedCount", installResult.installedCount());
        details.put("alreadyInstalledCount", installResult.alreadyInstalledCount());
        details.put("requestedInstalledCount", countMatching(installResult.installedFiles(), requestedFiles));
        details.put("requestedAlreadyInstalledCount", countMatching(installResult.alreadyInstalledFiles(), requestedFiles));
        details.put("changed", result.changed());
        details.put("manifestChanged", result.manifestChanged());
        details.put("lockChanged", result.lockChanged());
        details.put("pluginsDir", result.pluginsDir().toString());
        details.put("requested", requested.stream().map(ManifestEditor::requestId).toList());
        details.put("requestChanges", result.requestChanges().stream().map(RequestChange::label).toList());
        details.put("providerSwitchCount", result.requestChanges().stream().filter(RequestChange::providerSwitch).count());
        details.put("duplicateCleanupCount", result.requestChanges().stream().filter(RequestChange::duplicateCleanup).count());
        details.put("installedFiles", installResult.installedFiles());
        details.put("alreadyInstalledFiles", installResult.alreadyInstalledFiles());
        details.put("files", lock.getPlugins().stream().map(LockedPlugin::getFileName).toList());
        return details;
    }

    static String updateMessage(UpdateWorkflow.UpdateResult result) {
        List<String> ids = result.requestedIds();
        boolean server = result.server();
        PluginLock lock = result.lock();
        boolean changed = result.changed();
        if (server && ids.isEmpty()) {
            return changed
                    ? "Updated server and " + lock.getPlugins().size() + " plugin(s)"
                    : "Server and " + lock.getPlugins().size() + " plugin(s) already up to date";
        }
        if (!ids.isEmpty() && result.updatedPluginCount() == 0) {
            String noMatch = "no locked plugins matched " + String.join(", ", ids);
            if (server) {
                return changed ? "Updated server; " + noMatch : "Server already up to date; " + noMatch;
            }
            return "No locked plugins matched " + String.join(", ", ids);
        }
        if (!changed) {
            return ids.isEmpty()
                    ? lock.getPlugins().size() + " plugin(s) already up to date"
                    : result.updatedPluginCount() + " selected plugin(s) already up to date";
        }
        if (server) {
            return "Updated server and " + result.updatedPluginCount() + " selected plugin(s)";
        }
        return ids.isEmpty()
                ? "Updated " + lock.getPlugins().size() + " plugin(s)"
                : "Updated " + result.updatedPluginCount() + " selected plugin(s)";
    }

    static Map<String, ?> updateDetails(UpdateWorkflow.UpdateResult result, boolean interactive) {
        return Map.of(
                "count", result.updatedPluginCount(),
                "lockedCount", result.lock().getPlugins().size(),
                "requestedCount", result.requestedIds().size(),
                "updated", result.requestedIds(),
                "interactive", interactive,
                "server", result.server(),
                "changed", result.changed(),
                "lockChanged", result.lockChanged(),
                "serverDownloaded", result.serverDownloaded(),
                "plugins", result.lock().getPlugins().stream().map(LockedPlugin::getId).toList()
        );
    }

    static String removeMessage(RemoveWorkflow.RemoveResult result) {
        int lockRemovedCount = result.removedLockedPlugins().size();
        int manifestRemovedCount = result.removedManifestRequests().size();
        if (lockRemovedCount == 0 && manifestRemovedCount == 0) {
            return "Already removed " + String.join(", ", result.requested());
        }
        if (lockRemovedCount == 0) {
            return "Removed " + manifestRemovedCount + " plugin request(s)";
        }
        int manifestOnlyCount = Math.max(0, manifestRemovedCount - lockRemovedCount);
        if (manifestOnlyCount > 0) {
            return "Removed " + lockRemovedCount + " plugin(s) and "
                    + manifestOnlyCount + " plugin request(s)";
        }
        return "Removed " + lockRemovedCount + " plugin(s)";
    }

    static Map<String, ?> removeDetails(RemoveWorkflow.RemoveResult result) {
        return Map.of(
                "requested", result.requested(),
                "removed", result.removedLockedPlugins().stream().map(LockedPlugin::getId).toList(),
                "removedRequests", result.removedManifestRequests().stream().map(PluginRequest::getId).toList(),
                "lockRemovedCount", result.removedLockedPlugins().size(),
                "manifestRemovedCount", result.removedManifestRequests().size(),
                "deletedFiles", result.deletedFiles().stream().map(path -> path.getFileName().toString()).toList(),
                "manifestRemaining", result.manifest().getPlugins().size(),
                "lockRemaining", result.lock().getPlugins().size()
        );
    }

    static Map<String, ?> cleanInstallDetails(InstallWorkflow.CleanInstallResult result) {
        return Map.of(
                "count", result.lock().getPlugins().size(),
                "installedCount", result.installResult().installedCount(),
                "pluginsDir", result.pluginsDir().toString(),
                "files", result.lock().getPlugins().stream().map(LockedPlugin::getFileName).toList(),
                "changed", result.changed()
        );
    }

    private static String installChangePrefix(List<RequestChange> changes) {
        long providerSwitches = changes.stream().filter(RequestChange::providerSwitch).count();
        if (providerSwitches > 0) {
            return "Switched provider for " + providerSwitches + " plugin(s); ";
        }
        long duplicateCleanups = changes.stream().filter(RequestChange::duplicateCleanup).count();
        if (duplicateCleanups > 0) {
            return "Cleaned up " + duplicateCleanups + " duplicate plugin request(s); ";
        }
        return "";
    }

    private static List<String> lockedFilesForRequests(PluginLock lock, List<PluginRequest> requested) {
        return lock.getPlugins().stream()
                .filter(plugin -> requested.stream().anyMatch(request -> ManifestEditor.samePlugin(
                        request.getProvider(), request.getId(), plugin.getProvider(), plugin.getId())))
                .map(LockedPlugin::getFileName)
                .toList();
    }

    private static int countMatching(List<String> values, List<String> candidates) {
        return (int) values.stream().filter(value -> candidates.stream().anyMatch(candidate -> same(value, candidate))).count();
    }

    private static boolean same(String left, String right) {
        return blank(left).equalsIgnoreCase(blank(right));
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
