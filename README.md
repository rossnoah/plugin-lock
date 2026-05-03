<div align="center">

# plugin-lock

### A safer, cleaner way to manage Minecraft server plugins.

`plugin-lock` turns plugin management into something predictable: add plugins with one command, lock exact versions, verify downloads, and rebuild the same server later without guessing.

<p>
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-servers-62B47A?style=for-the-badge">
  <img alt="Paper" src="https://img.shields.io/badge/Paper-supported-2D3748?style=for-the-badge">
  <img alt="Purpur" src="https://img.shields.io/badge/Purpur-supported-A855F7?style=for-the-badge">
  <img alt="Modrinth" src="https://img.shields.io/badge/Modrinth-plugins-00AF5C?style=for-the-badge">
  <img alt="Hangar" src="https://img.shields.io/badge/Hangar-plugins-F59E0B?style=for-the-badge">
</p>

<p>
  <code>pl init</code>
  &nbsp;>&nbsp;
  <code>pl install luckperms viaversion chunky</code>
  &nbsp;>&nbsp;
  <code>pl doctor</code>
</p>

</div>

---

## The Pitch

Manual plugin management works until it does not.

You download a jar. Then another. Then a dependency. Then a newer Minecraft version comes out. A plugin breaks. You are not sure which version was installed. Your friend, teammate, host, CI job, or future self cannot reproduce the same server without copying the whole folder.

`plugin-lock` brings a package-manager workflow to Minecraft servers without making normal server owners learn a heavy toolchain.

<table>
  <tr>
    <td width="50%">
      <h3>For Server Owners</h3>
      <p>Install, update, remove, inspect, and repair plugins with guided commands instead of manually chasing jar downloads.</p>
      <p><code>pl install luckperms</code></p>
    </td>
    <td width="50%">
      <h3>For Developers and Power Users</h3>
      <p>Commit lockfiles, run strict installs, emit JSON, and rebuild servers consistently across machines and CI.</p>
      <p><code>pl ci</code> &nbsp; <code>pl --json doctor</code></p>
    </td>
  </tr>
</table>

## Why It Feels Better

| Instead of... | Use plugin-lock to... |
| --- | --- |
| Downloading jars by hand | Install plugins with `pl install <plugin>` |
| Forgetting exact versions | Record exact artifacts in `server-lock.lock.json` |
| Copying whole server folders | Rebuild from manifest + lockfile |
| Wondering what changed | Inspect with `pl list` |
| Debugging missing or tampered jars manually | Run `pl doctor` |
| Hoping a deployment matches your machine | Use strict `pl ci` |

## Feature Chips

<p>
  <code>Modrinth + Hangar</code>
  <code>Paper + Purpur</code>
  <code>SHA verification</code>
  <code>Lockfiles</code>
  <code>JSON output</code>
  <code>CI friendly</code>
  <code>Progress bars</code>
  <code>Server owner friendly</code>
</p>

## Quick Start

Install the local `pl` command:

```sh
./scripts/install-pl-local
```

Make sure `~/.local/bin` is on your `PATH`, then go to your server folder:

```sh
cd /path/to/server
pl init
pl install luckperms
```

Install a full starter set:

```sh
pl install viaversion luckperms viabackwards chunky betterrtp
```

Check your server state:

```sh
pl list
pl doctor
```

## Core Workflow

| Step | Command | What it does |
| --- | --- | --- |
| 1 | `pl init` | Creates the project files and downloads a Paper/Purpur server jar. |
| 2 | `pl install luckperms` | Adds, resolves, downloads, verifies, and locks a plugin. |
| 3 | `pl list` | Shows the locked server and plugin set. |
| 4 | `pl doctor` | Checks for missing files, hash mismatches, and lockfile problems. |
| 5 | `pl ci` | Reinstalls exactly from the lockfile. |

## Everyday Commands

<table>
  <tr>
    <th>Goal</th>
    <th>Command</th>
  </tr>
  <tr>
    <td>Start a server project</td>
    <td><code>pl init</code></td>
  </tr>
  <tr>
    <td>Install one plugin</td>
    <td><code>pl install luckperms</code></td>
  </tr>
  <tr>
    <td>Install several plugins</td>
    <td><code>pl install viaversion luckperms chunky</code></td>
  </tr>
  <tr>
    <td>Search providers</td>
    <td><code>pl search chunky</code></td>
  </tr>
  <tr>
    <td>List locked plugins</td>
    <td><code>pl list</code></td>
  </tr>
  <tr>
    <td>Check local health</td>
    <td><code>pl doctor</code></td>
  </tr>
  <tr>
    <td>Update plugins</td>
    <td><code>pl update</code></td>
  </tr>
  <tr>
    <td>Remove a plugin</td>
    <td><code>pl remove chunky</code></td>
  </tr>
  <tr>
    <td>Strict reinstall</td>
    <td><code>pl ci</code></td>
  </tr>
</table>

## Power User Mode

Use provider and version shorthand:

```sh
pl install luckperms@5.4.0
pl install modrinth:luckperms
pl install hangar:PlaceholderAPI
pl install hangar:PlaceholderAPI@2.11.6
```

Run against another server directory:

```sh
pl --project-dir /srv/minecraft install luckperms
pl --project-dir /srv/minecraft ci
```

Use JSON output in automation:

```sh
pl --json list
pl --json doctor
pl --json remove luckperms
```

Use verbose output when debugging:

```sh
pl --verbose install
```

## Command Highlights

### Initialize

```sh
pl init
pl init --server paper --minecraft 1.21.4 --yes
pl init --server purpur
```

`pl init` asks for server software and Minecraft version, downloads the selected Paper or Purpur server jar, and writes the initial project files.

### Install

```sh
pl install luckperms
pl install viaversion luckperms viabackwards chunky betterrtp
pl i luckperms
```

If the directory is empty, `pl install <plugin>` can initialize the project first:

```sh
pl install luckperms --minecraft 1.21.4 --server paper --yes
```

### Search

```sh
pl search luckperms
pl search chunky --provider modrinth
pl search placeholder --provider hangar
```

### Add Without Downloading

```sh
pl add luckperms
pl add modrinth:luckperms@5.4.0
pl add hangar:PlaceholderAPI --yes
```

`pl add` edits `server-lock.json`. Run `pl install` or `pl update` later to resolve and download.

### Update

```sh
pl update
pl update luckperms
pl update --server
```

`pl update` refreshes plugin versions from the manifest. `pl update --server` refreshes the locked Paper/Purpur server build too.

### Remove

```sh
pl remove luckperms
pl rm luckperms
pl uninstall luckperms
```

## What Gets Created

| File | Purpose |
| --- | --- |
| `server-lock.json` | Editable manifest for the plugins you want. |
| `server-lock.lock.json` | Generated lockfile with exact resolved artifacts. |
| `paper-*.jar` / `purpur-*.jar` | Selected server jar. |
| `plugins/*.jar` | Installed plugin jars. |

Commit `server-lock.json` and `server-lock.lock.json` if you want reproducible server setup in Git.

## Manifest Example

`server-lock.json` stays small and readable:

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

The lockfile is generated. It records exact selected versions and hashes. Treat it like `package-lock.json`: commit it, use it, but do not hand-edit it unless you know why.

## Install the Local CLI

Build and install a local `pl` launcher:

```sh
./scripts/install-pl-local
```

Make sure `~/.local/bin` is on your `PATH`.

Build a self-contained native app image:

```sh
./gradlew :plugin-lock-cli:jpackageImage
```

On macOS, the generated launcher is:

```sh
plugin-lock-cli/build/jpackage/output/pl.app/Contents/MacOS/pl
```

Build an OS-native installer:

```sh
./gradlew :plugin-lock-cli:jpackageInstaller
```

## Paper Plugin Mode

The CLI is the main experience. The repo also includes a Paper plugin for servers that should install from an existing lockfile.

```sh
./gradlew :plugin-lock-paper:jar
```

Put this jar in your server `plugins/` directory:

```txt
plugin-lock-paper/build/libs/plugin-lock-paper-0.1.0-SNAPSHOT.jar
```

Put `server-lock.lock.json` in the server root, start the server, then run:

```txt
/pluginlock install
```

Restart the server after installation so newly downloaded plugin jars load normally.

## Development

```sh
./gradlew build
./gradlew check
./gradlew test :plugin-lock-cli:integrationTest :plugin-lock-core:integrationTest
./gradlew :plugin-lock-core:liveIntegrationTest -PliveApi=true
```

Run the CLI through Gradle during development:

```sh
./gradlew :plugin-lock-cli:run --args="--help"
./gradlew :plugin-lock-cli:run --args="install luckperms"
```

## Modules

| Module | Responsibility |
| --- | --- |
| `plugin-lock-core` | Manifest, lockfile, provider resolution, download, and hash verification. |
| `plugin-lock-cli` | Native CLI entry point and packaging tasks. |
| `plugin-lock-paper` | Paper plugin entry point. |
