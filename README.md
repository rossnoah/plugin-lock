# plugin-lock

`plugin-lock` is a Java package manager for Minecraft server plugins. It uses a user-authored manifest and a generated lockfile so a server can install the same plugin artifacts every time.

The project exposes the same core behavior in two ways:

- `pl`: a native CLI for managing plugins from your terminal.
- `PluginLock`: a Paper plugin that can install from `plugin-lock.lock.json` on a Minecraft server.

The current provider implementation supports Modrinth project slugs/ids.

## Quick Start

Build and install the local `pl` command:

```sh
./scripts/install-pl-local
```

Make sure `~/.local/bin` is on your `PATH`, then create a plugin manifest in your server directory:

```sh
cd /path/to/server
pl init
pl install luckperms
```

This creates:

- `plugin-lock.json`: the editable manifest.
- `plugin-lock.lock.json`: selected server build plus exact resolved plugin artifacts.
- `paper-*.jar` or `purpur-*.jar`: the selected server jar downloaded during init.
- `plugins/*.jar`: downloaded plugin jars verified with SHA-512.

For repeatable installs from an existing lockfile:

```sh
pl ci
```

## CLI Commands

### `pl init`

Create a new `plugin-lock.json` and a starter `plugin-lock.lock.json`.

```sh
pl init
pl init --server paper --minecraft 1.21.4
pl init --server purpur
pl init --yes
```

`pl init` fetches available Paper and Purpur server versions from their downloads APIs, lets you choose the server software and Minecraft version, downloads the selected server jar, then records the selected server build in `plugin-lock.lock.json`. Paper downloads are verified with the API-provided SHA-256. Use `--yes` to accept the latest Paper build without prompts.

### `pl install` / `pl i`

Install plugins. With package names, this also adds them to the manifest and updates the lockfile.

```sh
pl install
pl install luckperms
pl i luckperms
```

When installing a new plugin, `pl` fetches provider metadata first and asks for confirmation before adding or downloading it. The confirmation summary includes the plugin name, authors, download count, and description.

Options:

```sh
pl install luckperms --provider modrinth --version latest
pl install --plugins-dir plugins
pl install luckperms --yes
```

### `pl clean-install` / `pl ci`

Install exactly from `plugin-lock.lock.json` without resolving newer versions. This is the CI/server equivalent of `npm ci`.

```sh
pl ci
pl clean-install --plugins-dir plugins
```

### `pl remove` / `pl rm` / `pl uninstall`

Remove plugins from the manifest, lockfile, and plugins directory.

```sh
pl remove luckperms
pl rm luckperms
pl uninstall luckperms
```

### `pl add`

Edit `plugin-lock.json` without installing immediately.

```sh
pl add luckperms
pl add luckperms --version latest
pl add luckperms --yes
```

`pl add` also asks for confirmation unless `--yes` is provided.

### Project Directory

Run commands against a server directory without changing shell directories:

```sh
pl --project-dir /path/to/server install luckperms
pl --project-dir /path/to/server ci
```

If you accidentally run `pl` from inside a server's `plugins/` directory, it automatically treats the parent directory as the server root:

```sh
cd /path/to/server/plugins
pl ci
```

## Native CLI Packaging

Build a self-contained native app image with an embedded Java runtime:

```sh
./gradlew :plugin-lock-cli:jpackageImage
```

On macOS, the generated launcher is:

```sh
plugin-lock-cli/build/jpackage/output/pl.app/Contents/MacOS/pl
```

Install a local symlink into `~/.local/bin`:

```sh
./scripts/install-pl-local
```

Build an OS-native installer:

```sh
./gradlew :plugin-lock-cli:jpackageInstaller
```

Override installer type or native package version:

```sh
./gradlew :plugin-lock-cli:jpackageInstaller -PinstallerType=dmg -PnativePackageVersion=1.0.0
```

## Paper Plugin

Build the Paper plugin jar:

```sh
./gradlew :plugin-lock-paper:jar
```

Put this jar in your server `plugins/` directory:

```txt
plugin-lock-paper/build/libs/plugin-lock-paper-0.1.0-SNAPSHOT.jar
```

Put `plugin-lock.lock.json` in the server root, start the server, then run:

```txt
/pluginlock install
```

Restart the server after installation so newly downloaded plugin jars load normally.

## Manifest

`plugin-lock.json` is the editable file:

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

`plugin-lock.lock.json` is generated. It records the resolved version id, download URL, filename, size, and SHA-512 hash for each plugin.

## Development

Build everything:

```sh
./gradlew build
```

Run unit tests and fake/local integration tests:

```sh
./gradlew check
```

Run live Modrinth API integration tests:

```sh
./gradlew :plugin-lock-core:liveIntegrationTest -PliveApi=true
```

The default `check` task excludes live API tests so normal local and CI runs do not depend on external service availability.

Run the CLI through Gradle during development:

```sh
./gradlew :plugin-lock-cli:run --args="--help"
./gradlew :plugin-lock-cli:run --args="install luckperms"
```

## Modules

- `plugin-lock-core`: manifest, lockfile, provider resolution, download, and hash verification.
- `plugin-lock-cli`: native CLI entry point and packaging tasks.
- `plugin-lock-paper`: Paper plugin entry point.
