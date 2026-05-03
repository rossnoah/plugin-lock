$ErrorActionPreference = "Stop"

$repo = if ($env:PLUGIN_LOCK_REPO) { $env:PLUGIN_LOCK_REPO } else { "rossnoah/plugin-lock" }
$version = if ($env:PLUGIN_LOCK_VERSION) { $env:PLUGIN_LOCK_VERSION } else { "latest" }
$installRoot = if ($env:PLUGIN_LOCK_HOME) { $env:PLUGIN_LOCK_HOME } else { Join-Path $HOME ".plugin-lock" }
$binDir = if ($env:PLUGIN_LOCK_BIN_DIR) { $env:PLUGIN_LOCK_BIN_DIR } else { Join-Path $HOME "bin" }

try {
    $javaMajor = (& java -version 2>&1 | Select-Object -First 1) -replace '.*version "([0-9]+).*', '$1'
    if ([int]$javaMajor -lt 21) {
        throw "Java 21 or newer is required"
    }
} catch {
    throw "Java 21 or newer is required"
}

if ($version -eq "latest") {
    $url = "https://github.com/$repo/releases/latest/download/pl.zip"
} else {
    if ($version.StartsWith("v")) {
        $tag = $version
    } else {
        $tag = "v$version"
    }
    $url = "https://github.com/$repo/releases/download/$tag/pl.zip"
}

$tmpDir = Join-Path ([System.IO.Path]::GetTempPath()) ("plugin-lock-" + [guid]::NewGuid())
$archive = Join-Path $tmpDir "pl.zip"

New-Item -ItemType Directory -Path $tmpDir | Out-Null
try {
    Write-Host "Downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $archive

    Remove-Item -Path $installRoot -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $binDir -Force | Out-Null
    Expand-Archive -Path $archive -DestinationPath $installRoot -Force

    $launcher = (Get-ChildItem "$installRoot\pl-*\bin\pl.bat" | Select-Object -First 1).FullName
    if (!$launcher) {
        throw "Launcher was not found after extraction"
    }

    $shim = Join-Path $binDir "pl.cmd"
    Set-Content -Path $shim -Encoding ASCII -Value "@echo off`r`n`"$launcher`" %*`r`n"

    Write-Host "Installed pl -> $shim"
    $pathEntries = $env:PATH -split [System.IO.Path]::PathSeparator
    if ($pathEntries -notcontains $binDir) {
        Write-Host "Add $binDir to your PATH to run pl from any terminal."
    }
} finally {
    Remove-Item -Path $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
}
