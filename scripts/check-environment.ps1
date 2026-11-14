$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot 'tools'

$nodeHome = Get-ChildItem -LiteralPath $toolsRoot -Directory -Filter 'node-*-win-x64' |
    Select-Object -First 1 -ExpandProperty FullName
$mavenCommand = Get-ChildItem -LiteralPath $toolsRoot -Directory -Filter 'apache-maven-3.*' |
    Select-Object -First 1 | ForEach-Object { Join-Path $_.FullName 'bin\mvn.cmd' }
$gitCommand = Get-ChildItem -LiteralPath (Join-Path $toolsRoot 'git') -Recurse -Filter 'git.exe' |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $nodeHome -or -not $mavenCommand -or -not $gitCommand) {
    throw 'Missing project-local development tool. See README.md.'
}

& (Join-Path $nodeHome 'node.exe') --version
& $mavenCommand --version | Select-Object -First 1
& $gitCommand --version
