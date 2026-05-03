<div align="center">

# plugin-lock

### A package manager for Minecraft server plugins.

<p>
  <img alt="Paper and Purpur" src="https://img.shields.io/badge/Paper%20%2F%20Purpur-supported-4f6f8f">
  <img alt="Modrinth and Hangar" src="https://img.shields.io/badge/Modrinth%20%2F%20Hangar-supported-6b5b95">
</p>

<p>
  <code>pl init</code>
  &nbsp;>&nbsp;
  <code>pl install luckperms viaversion</code>
  &nbsp;>&nbsp;
  <code>pl run</code>
</p>

</div>

---

## Install

`pl` requires Java 21 or newer.

macOS and Linux:

```sh
curl -fsSL https://raw.githubusercontent.com/rossnoah/plugin-lock/main/scripts/install-pl | sh
```

Windows PowerShell:

```powershell
irm https://raw.githubusercontent.com/rossnoah/plugin-lock/main/scripts/install-pl.ps1 | iex
```

## Quick Start

Open a terminal in your server folder:

```sh
pl init
pl install luckperms viaversion chunky
pl run
```

That creates the server files, installs plugins into `plugins/`, checks the folder, and starts the server.

## Common Commands

| Goal                                | Command                           |
| ----------------------------------- | --------------------------------- |
| Start a server folder               | `pl init`                         |
| Install plugins                     | `pl install luckperms viaversion` |
| Show what is locked                 | `pl list`                         |
| Check for missing or changed jars   | `pl doctor`                       |
| Update locked plugins               | `pl update`                       |
| Remove a plugin                     | `pl remove chunky`                |
| Reinstall exactly from the lockfile | `pl ci`                           |
| Start the server                    | `pl run`                          |

## Installing Plugins

Use plugin names:

```sh
pl install luckperms
pl install viaversion luckperms chunky
```

Pick a provider if needed:

```sh
pl install modrinth:luckperms
pl install hangar:PlaceholderAPI
```

Pin a version:

```sh
pl install luckperms@v5.5.17-bukkit
pl install hangar:PlaceholderAPI@2.11.6
```

Search before installing:

```sh
pl search luckperms
pl info luckperms
```

## License

MIT
