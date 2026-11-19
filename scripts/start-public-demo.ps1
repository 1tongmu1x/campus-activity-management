$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $repo 'tools'
$cloudflared = Join-Path $tools 'cloudflared\cloudflared.exe'
$caddy = Join-Path $tools 'deployment-validation\caddy.exe'
$node = Join-Path $tools 'node-v26.13.1-win-x64\node.exe'
$frontend = Join-Path $repo 'frontend'
$vite = Join-Path $frontend 'node_modules\vite\bin\vite.js'
$work = Join-Path $tools 'public-demo'
$site = Join-Path $work 'site'
$configPath = Join-Path $work 'caddy.json'
$tunnelLog = Join-Path $work 'tunnel.log'
$publicPort = 18181
$adminPort = 18182
$publicLocal = "http://127.0.0.1:$publicPort"
$adminLocal = "http://127.0.0.1:$adminPort"

foreach ($file in @($cloudflared, $caddy, $node, $vite)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Required file is missing: $file"
    }
}

try {
    $csrf = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/public/csrf' -TimeoutSec 5
    if (-not $csrf.token) { throw 'Missing registration token.' }
} catch {
    throw 'Start the current Campus Activity backend on port 8080 first, then run this script again.'
}

New-Item -ItemType Directory -Path $work -Force | Out-Null
$env:PUBLIC_HOST = $publicLocal
$env:ADMIN_HOST = $adminLocal
$env:ADMIN_ALLOWED_IPS = '127.0.0.1'
$env:BACKEND_UPSTREAM = '127.0.0.1:8080'
$env:WEB_ROOT = $site.Replace('\', '/')
$env:XDG_CONFIG_HOME = $work
$env:XDG_DATA_HOME = $work

$caddyfile = Join-Path $repo 'docker\Caddyfile'
$config = & $caddy adapt --config $caddyfile --adapter caddyfile | Out-String
if ($LASTEXITCODE -ne 0) { throw 'Caddy could not load the deployment configuration.' }
$config = ConvertFrom-Json $config
foreach ($server in $config.apps.http.servers.PSObject.Properties.Value) {
    # Caddyfile site addresses set host matching. Bind the listeners to loopback as well.
    $server.listen = @($server.listen | ForEach-Object {
        if ($_ -notin ":$publicPort", ":$adminPort") { throw "Unexpected Caddy listener: $_" }
        "127.0.0.1$_"
    })
}
$config | ConvertTo-Json -Depth 60 | Set-Content -LiteralPath $configPath -Encoding Ascii

$gateway = $null
$tunnel = $null
$savedOrigin = $env:VITE_PUBLIC_ORIGIN
try {
    $gateway = Start-Process -FilePath $caddy -ArgumentList "run --config `"$configPath`"" `
        -WorkingDirectory $work -RedirectStandardError (Join-Path $work 'gateway.log') -WindowStyle Hidden -PassThru

    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        if ($gateway.HasExited) { throw "Could not start the gateway. See $work\gateway.log" }
        try {
            $csrf = Invoke-RestMethod -Uri "$publicLocal/api/public/csrf" -TimeoutSec 2
            if ($csrf.token) { break }
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    if ($attempt -eq 20) { throw "The gateway could not reach the backend. See $work\gateway.log" }

    Write-Host 'Creating a temporary HTTPS address...'
    $tunnelArgs = "tunnel --no-autoupdate --protocol http2 --url $publicLocal --http-host-header 127.0.0.1:$publicPort"
    $tunnel = Start-Process -FilePath $cloudflared -ArgumentList $tunnelArgs `
        -WorkingDirectory $work -RedirectStandardError $tunnelLog -WindowStyle Hidden -PassThru

    $publicOrigin = $null
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($tunnel.HasExited) { throw "Could not start Cloudflare Tunnel. See $tunnelLog" }
        $log = Get-Content -LiteralPath $tunnelLog -Raw -ErrorAction SilentlyContinue
        if ($log -match 'https://[a-z0-9-]+\.trycloudflare\.com\b' -and $log -match 'Registered tunnel connection') {
            $publicOrigin = [regex]::Match($log, 'https://[a-z0-9-]+\.trycloudflare\.com\b').Value
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $publicOrigin) { throw "No live tunnel address was received. See $tunnelLog" }

    $env:VITE_PUBLIC_ORIGIN = $publicOrigin
    Push-Location -LiteralPath $frontend
    try {
        & $node $vite build --configLoader runner --outDir $site
        if ($LASTEXITCODE -ne 0) { throw 'The frontend build failed.' }
    } finally {
        Pop-Location
    }

    if ($gateway.HasExited -or $tunnel.HasExited) { throw "The gateway or tunnel stopped. See logs in $work" }
    Write-Host "Admin page: $adminLocal/"
    Write-Host "Public address: $publicOrigin"
    Write-Host 'Use Share Registration in the admin page to get the full activity link.'
    Write-Host 'Keep this window open. Press Ctrl+C to stop the public demo.'
    while (-not $gateway.HasExited -and -not $tunnel.HasExited) { Start-Sleep -Seconds 1 }
    throw "The gateway or tunnel stopped. See logs in $work"
} finally {
    $env:VITE_PUBLIC_ORIGIN = $savedOrigin
    foreach ($process in @($tunnel, $gateway)) {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -InputObject $process -ErrorAction SilentlyContinue
        }
    }
}
