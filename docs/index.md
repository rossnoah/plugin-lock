---
layout: default
title: plugin-lock docs
permalink: /
---

<section class="home-hero">
  <p class="lead"><code>pl</code> initializes Paper and Purpur server folders, installs exact plugin jars from a lockfile, checks local files against recorded hashes, and starts the locked server.</p>

  <pre class="command"><code>pl init
pl install luckperms viaversion chunky
pl run</code></pre>
</section>

<section class="install-section">
  <h2>Install</h2>
  <p>Java 21 or newer is required. On Windows, <code>JAVA_HOME</code> is used first when it is set, then <code>java</code> on <code>PATH</code>.</p>

  <div class="install-commands">
    <div class="command-entry">
      <h3>macOS and Linux</h3>
      <pre class="command"><code>curl -fsSL https://raw.githubusercontent.com/rossnoah/plugin-lock/main/scripts/install-pl | sh</code></pre>
    </div>
    <div class="command-entry">
      <h3>Windows PowerShell</h3>
      <pre class="command"><code>irm https://raw.githubusercontent.com/rossnoah/plugin-lock/main/scripts/install-pl.ps1 | iex</code></pre>
    </div>
  </div>
</section>

## Command Reference {#command-reference}

The reference below documents the full `pl` command surface: usage, inputs, outputs, files touched, and exit behavior.

### Contents

<nav class="toc" aria-label="Table of contents">
  <ol>
    <li><a href="#mental-model">Mental Model</a></li>
    <li><a href="#plugin-coordinates">Plugin Coordinates</a></li>
    <li><a href="#global-options">Global Options</a></li>
    <li><a href="#commands-at-a-glance">Commands At A Glance</a></li>
    <li><a href="#commands">Commands</a>
      <ol>
        <li><a href="#pl-init"><code>pl init</code></a></li>
        <li><a href="#pl-add"><code>pl add</code></a></li>
        <li><a href="#pl-lock"><code>pl lock</code></a></li>
        <li><a href="#pl-install"><code>pl install</code></a></li>
        <li><a href="#pl-clean-install"><code>pl clean-install</code></a></li>
        <li><a href="#pl-remove"><code>pl remove</code></a></li>
        <li><a href="#pl-list"><code>pl list</code></a></li>
        <li><a href="#pl-doctor"><code>pl doctor</code></a></li>
        <li><a href="#pl-update"><code>pl update</code></a></li>
        <li><a href="#pl-search"><code>pl search</code></a></li>
        <li><a href="#pl-info"><code>pl info</code></a></li>
        <li><a href="#pl-run"><code>pl run</code></a></li>
      </ol>
    </li>
    <li><a href="#exit-codes">Exit Codes</a></li>
    <li><a href="#file-shapes">File Shapes</a></li>
  </ol>
</nav>

## 1 Mental Model {#mental-model}

`plugin-lock` manages a Minecraft server folder with two project files:

| File                    | Purpose                                                                                                          |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `server-lock.json`      | Editable manifest. Stores the Minecraft version, loader, optional run memory, and requested plugins.             |
| `server-lock.lock.json` | Resolved lockfile. Stores the exact server jar and plugin artifacts, download URLs, versions, sizes, and hashes when providers supply them. |

Plugin jars are installed into `plugins/` by default. Relative paths are resolved from the detected project root.

## 2 Plugin Coordinates {#plugin-coordinates}

Commands that create plugin requests, such as `pl add` and `pl install`, support this coordinate syntax:

| Syntax                         | Meaning                                                                |
| ------------------------------ | ---------------------------------------------------------------------- |
| `luckperms`                    | Use the command provider option, usually `auto`, and version `latest`. |
| `modrinth:luckperms`           | Force a provider.                                                      |
| `luckperms@v5.5.17-bukkit`     | Pin a version id or version number.                                    |
| `hangar:PlaceholderAPI@2.11.6` | Force provider and version.                                            |

`pl info` also accepts provider shorthand such as `hangar:PlaceholderAPI`; it does not create a manifest request or pin a version.

Supported plugin providers are `modrinth`, `hangar`, and `auto`. For plugin requests, `auto` checks both providers and chooses or prompts from exact provider matches.

Supported server providers are `paper` and `purpur`. A Purpur server still uses the `paper` plugin loader value in `server-lock.json`.

## 3 Global Options {#global-options}

Usage:

```sh
pl [--project-dir DIR] [--verbose] [--json] <command> [command-options]
```

Running `pl` with no command prints usage and exits successfully.

| Option              | Meaning                                                                                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `--project-dir DIR` | Project root containing `server-lock.json` or `server-lock.lock.json`. Defaults to the current directory. If it points at a `plugins/` folder, the parent is used as the server root. |
| `--verbose`         | In text mode, print structured command details after success messages.                                                                                                                |
| `--json`            | Emit one-line JSON envelopes instead of text output.                                                                                                                                  |
| `-h`, `--help`      | Show command help.                                                                                                                                                                    |
| `-V`, `--version`   | Show the `plugin-lock` version.                                                                                                                                                       |

JSON output uses this envelope:

```json
{
  "status": "success",
  "command": "install",
  "message": "Installed 1 locked plugin(s)",
  "details": {}
}
```

Warnings are emitted as separate `warning` envelopes when `--json` is enabled.

## 4 Commands At A Glance {#commands-at-a-glance}

| Command            | Aliases                 | Purpose                                                                  |
| ------------------ | ----------------------- | ------------------------------------------------------------------------ |
| `pl init`          |                         | Create project files and download a locked server jar.                   |
| `pl add`           |                         | Add or update a plugin request in `server-lock.json`.                    |
| `pl lock`          |                         | Resolve `server-lock.json` into `server-lock.lock.json`. Hidden command. |
| `pl install`       | `pl i`                  | Resolve if needed and install locked plugin jars.                        |
| `pl clean-install` | `pl ci`                 | Reinstall exactly from the lockfile.                                     |
| `pl remove`        | `pl rm`, `pl uninstall` | Remove plugins from project files and delete installed jars.             |
| `pl list`          | `pl ls`                 | Show locked server and plugin artifacts.                                 |
| `pl doctor`        |                         | Check project files, jars, and hashes.                                   |
| `pl update`        |                         | Refresh locked plugin versions, and optionally the server jar.           |
| `pl search`        |                         | Search plugin providers.                                                 |
| `pl info`          |                         | Show plugin metadata and available versions.                             |
| `pl run`           |                         | Start the locked server jar.                                             |

## 5 Commands {#commands}

### 5.1 `pl init` {#pl-init}

Create `server-lock.json`, create `server-lock.lock.json`, and download the selected server jar.

```sh
pl init [--minecraft VERSION] [--server paper|purpur] [-y|--yes]
```

| Field           | Value                                                                                                   |
| --------------- | ------------------------------------------------------------------------------------------------------- |
| Reads           | Paper or Purpur version/build APIs.                                                                     |
| Writes          | `server-lock.json`, `server-lock.lock.json`, and the server jar in the project root.                    |
| Default server  | `paper`.                                                                                                |
| Default version | Latest stable version returned by the selected server API, or `1.21.4` if no version list is available. |
| Exit            | `0` on success or already initialized. `1` on validation/download errors.                               |

Options:

| Option                | Meaning                                         |
| --------------------- | ----------------------------------------------- |
| `--minecraft VERSION` | Use a specific Minecraft version.               |
| <code>--server paper&#124;purpur</code> | Use a specific server provider.              |
| `-y`, `--yes`         | Accept detected/default values without prompts. |

Behavior:

- If `server-lock.json` already exists, the command reports the existing project and does not overwrite it.
- Without `--yes`, it prompts for server software and Minecraft version.
- A matching server jar is reused when the selected provider supplies SHA-256 data and the local file matches it.

Examples:

```sh
pl init
pl init --minecraft 1.21.4 --server paper --yes
pl init --server purpur
```

### 5.2 `pl add` {#pl-add}

Add a plugin request to the manifest without installing it.

```sh
pl add <plugin> [--provider auto|modrinth|hangar] [--version VERSION|latest] [-y|--yes]
```

| Field          | Value                                                                         |
| -------------- | ----------------------------------------------------------------------------- |
| Reads          | Existing `server-lock.json` if present, provider metadata APIs.               |
| Writes         | `server-lock.json`.                                                           |
| Does not write | `server-lock.lock.json` or plugin jars.                                       |
| Exit           | `0` on added, updated, switched, or already present. `1` on cancel or errors. |

Options:

| Option             | Meaning                                               |
| ------------------ | ----------------------------------------------------- |
| <code>--provider auto&#124;modrinth&#124;hangar</code> | Provider for the plugin unless the coordinate includes a provider. Default: `auto`. |
| <code>--version VERSION&#124;latest</code> | Version id, version number, or `latest` unless the coordinate includes a version. Default: `latest`. |
| `-y`, `--yes`      | Skip confirmation and use the default provider match. |

Behavior:

- Creates `server-lock.json` if it is missing, using default project values.
- Provider/version in `<plugin>` override `--provider` and `--version`.
- Removes duplicate logical requests before adding the new one. For example, switching `viaversion` from Modrinth to Hangar leaves one request.
- With `auto`, exact provider matches are checked across Modrinth and Hangar. Interactive mode lets you choose by provider number.

Examples:

```sh
pl add luckperms
pl add modrinth:luckperms
pl add hangar:PlaceholderAPI@2.11.6
pl add luckperms@v5.5.17-bukkit --provider modrinth --yes
```

### 5.3 `pl lock` {#pl-lock}

Resolve the editable manifest into a lockfile. This command is hidden from the main help output, but it is callable.

```sh
pl lock
```

| Field  | Value                                                                          |
| ------ | ------------------------------------------------------------------------------ |
| Reads  | `server-lock.json`, existing `server-lock.lock.json` if present.               |
| Writes | `server-lock.lock.json` when resolved plugin data changed.                     |
| Exit   | `0` on locked or already locked. `1` on missing manifest or resolution errors. |

Behavior:

- Resolves every manifest plugin to an exact downloadable artifact.
- Preserves existing locked server data when a lockfile already exists.
- Emits compatibility warnings when resolved plugins do not clearly match the configured Minecraft version or loader.

Examples:

```sh
pl lock
pl --json lock
```

### 5.4 `pl install` {#pl-install}

Install plugin jars and update project files when a manifest is present.

```sh
pl install [plugins...] [--provider auto|modrinth|hangar] [--version VERSION|latest] [--plugins-dir DIR] [--minecraft VERSION] [--server paper|purpur] [-y|--yes]
```

Alias:

```sh
pl i
```

| Field               | Value                                                                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Reads               | `server-lock.json`, `server-lock.lock.json`, provider APIs, plugin download URLs.                                                                                   |
| Writes              | `server-lock.json`, `server-lock.lock.json`, and plugin jars in the plugins directory. It may also create project files and download a server jar during auto-init. |
| Default plugins dir | `plugins/` under the project root.                                                                                                                                  |
| Exit                | `0` on success. `1` on missing project files, resolution errors, download errors, or hash mismatches.                                                               |

Options:

| Option                | Meaning                                                                                              |
| --------------------- | ---------------------------------------------------------------------------------------------------- |
| `plugins...`          | Optional plugin coordinates to add before installing.                                                |
| <code>--provider auto&#124;modrinth&#124;hangar</code> | Provider for new plugin arguments. Default: `auto`.                                  |
| <code>--version VERSION&#124;latest</code> | Version for new plugin arguments. Default: `latest`.                                      |
| `--plugins-dir DIR`   | Destination plugin directory. Relative paths are resolved from the project root. Default: `plugins`. |
| `--minecraft VERSION` | Minecraft version for auto-init, or a manifest version update before resolving.                      |
| <code>--server paper&#124;purpur</code> | Server provider for auto-init when no project files exist.                                       |
| `-y`, `--yes`         | Skip plugin metadata confirmation and accept default provider matches.                               |

Behavior:

- With no plugin arguments and only a lockfile present, installs exactly the locked plugin jars without resolving new versions.
- With a manifest present, resolves the manifest, updates the lockfile if needed, then installs all locked plugins.
- With plugin arguments, adds those requests to the manifest, resolves the lockfile, then installs all locked plugins.
- If no project files exist and plugin arguments or init options are supplied, it initializes the project first.
- If an interactive plugin selection is cancelled, that plugin argument is skipped and the command continues.
- Existing plugin jars are skipped when their recorded SHA-512 or SHA-256 matches.
- Plugin downloads are written through temporary files and moved into place after hash verification.

Examples:

```sh
pl install
pl install luckperms viaversion chunky
pl install hangar:PlaceholderAPI@2.11.6 --yes
pl install --plugins-dir ./server-plugins
pl install luckperms --minecraft 1.21.4 --server paper --yes
```

### 5.5 `pl clean-install` {#pl-clean-install}

Reinstall exactly the plugin artifacts recorded in the lockfile.

```sh
pl clean-install [--plugins-dir DIR]
pl ci [--plugins-dir DIR]
```

| Field            | Value                                                                                   |
| ---------------- | --------------------------------------------------------------------------------------- |
| Reads            | `server-lock.lock.json`, plugin download URLs.                                          |
| Writes           | Plugin jars in the plugins directory.                                                   |
| Deletes          | Only locked plugin jar filenames from the target plugins directory before reinstalling. |
| Does not resolve | Manifest plugins or new versions.                                                       |
| Exit             | `0` on success. `1` on missing lockfile, download errors, or hash mismatches.           |

Options:

| Option              | Meaning                                                                                              |
| ------------------- | ---------------------------------------------------------------------------------------------------- |
| `--plugins-dir DIR` | Destination plugin directory. Relative paths are resolved from the project root. Default: `plugins`. |

Examples:

```sh
pl ci
pl clean-install --plugins-dir plugins
```

### 5.6 `pl remove` {#pl-remove}

Remove plugins from project files and delete their installed jars.

```sh
pl remove <ids...> [--plugins-dir DIR]
pl rm <ids...> [--plugins-dir DIR]
pl uninstall <ids...> [--plugins-dir DIR]
```

| Field   | Value                                                                                                                         |
| ------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Reads   | `server-lock.json` and/or `server-lock.lock.json`.                                                                            |
| Writes  | Updated manifest and lockfile when present.                                                                                   |
| Deletes | Installed jar files for removed locked plugins.                                                                               |
| Exit    | `0` when removal succeeds or nothing matched in an existing project. `1` when no project files exist or another error occurs. |

Options:

| Option              | Meaning                                                         |
| ------------------- | --------------------------------------------------------------- |
| `<ids...>`          | Plugin ids to remove. Matching is case-insensitive.             |
| `--plugins-dir DIR` | Directory containing installed plugin jars. Default: `plugins`. |

Matching:

- Lockfile plugins match by provider id, display name, jar filename, or jar filename without `.jar`.
- Manifest requests match by request id, or by provider/id from a removed locked plugin.

Examples:

```sh
pl remove chunky
pl rm viaversion luckperms
pl uninstall LuckPerms-Bukkit-5.5.17 --plugins-dir plugins
```

### 5.7 `pl list` {#pl-list}

Show the locked server and plugin artifacts.

```sh
pl list
pl ls
```

| Field  | Value                                                         |
| ------ | ------------------------------------------------------------- |
| Reads  | `server-lock.lock.json`.                                      |
| Writes | Nothing.                                                      |
| Exit   | `0` on success. `1` if the lockfile is missing or unreadable. |

Behavior:

- Text output prints Minecraft version, loader, locked server jar, and each locked plugin.
- JSON output includes `minecraftVersion`, `loader`, `server`, and `plugins` in `details`.

Examples:

```sh
pl list
pl --json list
pl ls
```

### 5.8 `pl doctor` {#pl-doctor}

Check project files, installed artifacts, and recorded hashes.

```sh
pl doctor [--plugins-dir DIR]
```

| Field  | Value                                                                                                      |
| ------ | ---------------------------------------------------------------------------------------------------------- |
| Reads  | `server-lock.json`, `server-lock.lock.json`, server jar, plugin jars.                                      |
| Writes | Nothing.                                                                                                   |
| Exit   | `0` when there are no errors. `1` when any check has error status. Warnings alone do not fail the command. |

Options:

| Option              | Meaning                                                         |
| ------------------- | --------------------------------------------------------------- |
| `--plugins-dir DIR` | Directory containing installed plugin jars. Default: `plugins`. |

Checks:

- Manifest exists. Missing manifest is a warning.
- Lockfile exists. Missing lockfile is an error.
- Manifest and lockfile Minecraft versions match.
- Manifest and lockfile loaders match.
- Lockfile covers every manifest plugin request.
- Locked server jar exists and matches SHA-256 when a server hash is recorded.
- Locked plugin jars exist and match SHA-512 or SHA-256.
- Plugin compatibility warnings are reported as warnings.

Examples:

```sh
pl doctor
pl doctor --plugins-dir ./plugins
pl --json doctor
```

### 5.9 `pl update` {#pl-update}

Refresh locked plugin versions from the manifest. Optionally refresh the locked server jar too.

```sh
pl update [plugins...] [--server] [--interactive]
```

| Field          | Value                                                                                                                     |
| -------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Reads          | `server-lock.json`, existing `server-lock.lock.json`, provider APIs, server APIs when `--server` is set.                  |
| Writes         | `server-lock.lock.json` when changed. Downloads the resolved server jar when `--server` is set; a matching SHA-256 local jar is reused when the provider supplies hash data. |
| Does not write | Plugin jars. Run `pl install` after updating to download updated plugin artifacts.                                        |
| Exit           | `0` on success, already up to date, or no selected plugin matches. `1` on missing manifest or resolution/download errors. |

Options:

| Option          | Meaning                                                                                                   |
| --------------- | --------------------------------------------------------------------------------------------------------- |
| `plugins...`    | Optional plugin ids, names, jar filenames, or jar filename stems to update. Matching is case-insensitive. |
| `--server`      | Refresh the locked server build and download or reuse the server jar.                                     |
| `--interactive` | Prompt for which locked plugins to update.                                                                |

Behavior:

- With no plugin ids, resolves the manifest and updates all locked plugins.
- With plugin ids and an existing lockfile, only matching locked plugins are replaced with newly resolved entries. Unselected locked plugins are preserved.
- If no lockfile exists, `pl update` writes a full resolved lockfile from the manifest.
- Interactive selection accepts comma-separated numbers or names. Blank, `all`, or `*` means all plugins. `n`, `no`, `e`, `exit`, `q`, `quit`, `x`, or `cancel` cancels selection.
- `--server` uses the existing locked server provider when present. Otherwise it falls back to the manifest loader, then `paper`.

Examples:

```sh
pl update
pl update luckperms viaversion
pl update --interactive
pl update --server
pl update --server && pl install
```

### 5.10 `pl search` {#pl-search}

Search plugin providers.

```sh
pl search <query> [--provider auto|modrinth|hangar] [--limit N]
```

| Field  | Value                                                                                 |
| ------ | ------------------------------------------------------------------------------------- |
| Reads  | Provider search APIs.                                                                 |
| Writes | Nothing.                                                                              |
| Exit   | `0` on success, including no matches. `1` on unsupported provider or provider errors. |

Options:

| Option           | Meaning                                                                      |
| ---------------- | ---------------------------------------------------------------------------- |
| `<query>`        | Search text.                                                                 |
| <code>--provider auto&#124;modrinth&#124;hangar</code> | Provider to search. `auto` searches both. Default: `auto`. |
| `--limit N`      | Maximum results to show. Values below `1` are treated as `1`. Default: `10`. |

Behavior:

- `auto` combines Modrinth and Hangar results, sorts by download count descending, and applies the limit.

Examples:

```sh
pl search luckperms
pl search permissions --provider modrinth --limit 5
pl --json search viaversion
```

### 5.11 `pl info` {#pl-info}

Show provider metadata and available versions for a plugin.

```sh
pl info <plugin> [--provider auto|modrinth|hangar] [--minecraft VERSION] [--loader LOADER] [--limit N]
```

| Field  | Value                                                                               |
| ------ | ----------------------------------------------------------------------------------- |
| Reads  | `server-lock.json` if present, provider metadata/version APIs.                      |
| Writes | Nothing.                                                                            |
| Exit   | `0` on success. `1` on unsupported provider, no provider match, or provider errors. |

Options:

| Option                | Meaning                                                                                    |
| --------------------- | ------------------------------------------------------------------------------------------ |
| `<plugin>`            | Provider project slug or id. Provider shorthand is supported.                              |
| <code>--provider auto&#124;modrinth&#124;hangar</code> | Provider to inspect. Default: `auto`.                                      |
| `--minecraft VERSION` | Minecraft version used for compatibility markers. Defaults to the manifest value.          |
| `--loader LOADER`     | Loader used for version queries. Defaults to the manifest value.                           |
| `--limit N`           | Maximum versions to show per provider. Values below `1` are treated as `1`. Default: `12`. |

Behavior:

- With `auto`, exact provider matches are inspected across Modrinth and Hangar.
- Text output marks versions compatible with the selected Minecraft version as `OK`.
- The version list is limited; it is not a full version database export.

Examples:

```sh
pl info luckperms
pl info hangar:PlaceholderAPI --minecraft 1.21.4 --loader paper
pl --json info viaversion --limit 3
```

### 5.12 `pl run` {#pl-run}

Start the locked server jar with optimized JVM flags.

```sh
pl run [-m|--memory SIZE] [--jar PATH] [--java COMMAND] [--dry-run]
```

| Field  | Value                                                                                                                                                            |
| ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Reads  | `server-lock.lock.json` unless `--jar` is supplied. Reads `server-lock.json` for stored run memory when present.                                                 |
| Writes | `server-lock.json` only when memory is prompted and stored for the first time.                                                                                   |
| Starts | A Java process in the project root unless `--dry-run` is set.                                                                                                    |
| Exit   | The server process exit code. `0` for successful dry-run. `1` on missing lock/server jar data or invalid memory. `130` when interrupted during CLI-managed work. |

Options:

| Option                | Meaning                                                                                                  |
| --------------------- | -------------------------------------------------------------------------------------------------------- |
| `-m`, `--memory SIZE` | Heap size for both `-Xms` and `-Xmx`, such as `2G`, `2048M`, or `2048`.                                  |
| `--jar PATH`          | Server jar to run. Relative paths are resolved from the project root. Defaults to the locked server jar. |
| `--java COMMAND`      | Java executable. Default: `java`.                                                                        |
| `--dry-run`           | Print the Java command without starting the server.                                                      |

Memory behavior:

- `2048` becomes `2048M`.
- `2gb` becomes `2G`.
- If `--memory` is omitted, `runMemory` from `server-lock.json` is used when present.
- If no memory is stored, the command prompts with default `2G` and writes the selected value to `server-lock.json`.
- Passing `--memory` does not overwrite stored `runMemory`.

Examples:

```sh
pl run
pl run --memory 4G
pl run --jar paper-1.21.4-101.jar --dry-run
pl run --java /usr/lib/jvm/java-21/bin/java
```

## 6 Exit Codes {#exit-codes}

| Code  | Meaning                                                                                                                            |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `0`   | Command completed successfully. Some commands also return `0` for "already up to date", "already removed", or "no matches" states. |
| `1`   | Runtime error, validation error, provider/download/hash error, `doctor` errors, or cancelled `add`.                                |
| `130` | Operation cancelled by interruption.                                                                                               |

Parse and usage errors are handled by picocli before command execution.

## 7 File Shapes {#file-shapes}

Minimal manifest:

```json
{
  "minecraftVersion": "1.21.4",
  "loader": "paper",
  "plugins": [
    {
      "id": "luckperms",
      "provider": "modrinth",
      "version": "latest"
    }
  ]
}
```

Manifest with stored run memory:

```json
{
  "minecraftVersion": "1.21.4",
  "loader": "paper",
  "runMemory": "2G",
  "plugins": []
}
```

Lockfile shape:

```json
{
  "lockfileVersion": 1,
  "server": {
    "provider": "paper",
    "minecraftVersion": "1.21.4",
    "build": "101",
    "fileName": "paper-1.21.4-101.jar",
    "downloadUrl": "https://...",
    "sha256": "...",
    "size": 0
  },
  "minecraftVersion": "1.21.4",
  "loader": "paper",
  "generatedAt": "2026-05-03T00:00:00Z",
  "plugins": [
    {
      "id": "luckperms",
      "name": "LuckPerms",
      "provider": "modrinth",
      "projectId": "...",
      "versionId": "...",
      "versionName": "...",
      "fileName": "LuckPerms-Bukkit.jar",
      "downloadUrl": "https://...",
      "sha512": "...",
      "sha256": "...",
      "size": 0
    }
  ]
}
```
