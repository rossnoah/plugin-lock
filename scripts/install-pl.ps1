$ErrorActionPreference = "Stop"

$repo = if ($env:PLUGIN_LOCK_REPO) { $env:PLUGIN_LOCK_REPO } else { "rossnoah/plugin-lock" }
$version = if ($env:PLUGIN_LOCK_VERSION) { $env:PLUGIN_LOCK_VERSION } else { "latest" }
$installRoot = if ($env:PLUGIN_LOCK_HOME) { $env:PLUGIN_LOCK_HOME } else { Join-Path $HOME ".plugin-lock" }
$binDir = if ($env:PLUGIN_LOCK_BIN_DIR) { $env:PLUGIN_LOCK_BIN_DIR } else { Join-Path $HOME "bin" }

function Get-JavaVersionText {
    param([string]$JavaExecutable)

    $output = & $JavaExecutable -version 2>&1
    return ($output | ForEach-Object { "$_" }) -join "`n"
}

function Get-JavaMajorVersion {
    param([string]$VersionText)

    if ($VersionText -match 'version "1\.(\d+)') {
        return [int]$Matches[1]
    }
    if ($VersionText -match 'version "(\d+)') {
        return [int]$Matches[1]
    }
    if ($VersionText -match '^\S+\s+(\d+)(?:\.|\s|$)') {
        return [int]$Matches[1]
    }

    return $null
}

function Assert-Java21 {
    param(
        [string]$JavaExecutable,
        [string]$Source
    )

    try {
        $versionText = Get-JavaVersionText $JavaExecutable
        $javaMajor = Get-JavaMajorVersion $versionText
    } catch {
        throw "Java 21 or newer is required, but $Source could not be executed."
    }

    if ($null -eq $javaMajor) {
        throw "Java 21 or newer is required, but the version from $Source could not be parsed."
    }
    if ($javaMajor -lt 21) {
        throw "Java 21 or newer is required; $Source is Java $javaMajor."
    }
}

if ($env:JAVA_HOME) {
    $javaHome = $env:JAVA_HOME -replace '"', ''
    $javaExe = Join-Path $javaHome "bin\java.exe"
    if (!(Test-Path $javaExe)) {
        throw "Java 21 or newer is required, but JAVA_HOME does not point to a valid Java install: $javaHome"
    }
    Assert-Java21 $javaExe "JAVA_HOME ($javaHome)"
} else {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (!$javaCommand) {
        throw "Java 21 or newer is required. Install Java 21+ or set JAVA_HOME to a Java 21+ install."
    }
    Assert-Java21 $javaCommand.Source "java on PATH ($($javaCommand.Source))"
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
