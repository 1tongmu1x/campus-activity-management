param(
    [switch]$BackendOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot 'tools'
$mavenCommand = Get-ChildItem -LiteralPath $toolsRoot -Directory -Filter 'apache-maven-3.*' |
    Select-Object -First 1 | ForEach-Object { Join-Path $_.FullName 'bin\mvn.cmd' }
$nodeHome = Get-ChildItem -LiteralPath $toolsRoot -Directory -Filter 'node-*-win-x64' |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $mavenCommand -or -not $nodeHome) {
    throw 'Missing project-local Maven or Node.js. Run scripts/check-environment.ps1.'
}

$mavenRepository = (Join-Path $projectRoot '.m2').Replace('\', '/')
& $mavenCommand "-Dmaven.repo.local=$mavenRepository" -f (Join-Path $projectRoot 'backend\pom.xml') test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $BackendOnly) {
    $env:npm_config_cache = "$projectRoot\.npm-cache"
    $env:Path = "$nodeHome;$env:Path"
    & (Join-Path $nodeHome 'npm.cmd') --prefix "$projectRoot\frontend" install
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & (Join-Path $nodeHome 'npm.cmd') --prefix "$projectRoot\frontend" run build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
