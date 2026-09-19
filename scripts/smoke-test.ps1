param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ResolvedJar = (Resolve-Path $JarPath).Path
$SmokeRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("agent-framework-smoke-" + [guid]::NewGuid())

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed with exit code $LASTEXITCODE" }
}

try {
    New-Item -ItemType Directory -Path $SmokeRoot | Out-Null
    foreach ($Repository in @("order-service", "payment-service")) {
        $Source = Join-Path $ProjectRoot "examples\sample-multi-repo\$Repository"
        $Destination = Join-Path $SmokeRoot $Repository
        Copy-Item -Recurse $Source $Destination
        Invoke-Checked "git" @("-C", $Destination, "init", "--initial-branch=main", "--quiet")
        Invoke-Checked "git" @("-C", $Destination, "config", "user.name", "Agent Framework Smoke Test")
        Invoke-Checked "git" @("-C", $Destination, "config", "user.email", "smoke@example.invalid")
        Invoke-Checked "git" @("-C", $Destination, "add", ".")
        Invoke-Checked "git" @("-C", $Destination, "commit", "--quiet", "-m", "sample baseline")
    }

    $Configuration = @{ repositories = @(
        @{ name = "order-service"; localPath = (Join-Path $SmokeRoot "order-service"); defaultBranch = "main" },
        @{ name = "payment-service"; localPath = (Join-Path $SmokeRoot "payment-service"); defaultBranch = "main" }
    ) }
    $ConfigPath = Join-Path $SmokeRoot "repositories.json"
    $Configuration | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 $ConfigPath
    $DbPath = Join-Path $SmokeRoot "context.db"

    Invoke-Checked "java" @("-jar", $ResolvedJar, "init", "--db", $DbPath)
    Invoke-Checked "java" @("-jar", $ResolvedJar, "index", "--db", $DbPath, "--config", $ConfigPath)

    $ContextPath = Join-Path $SmokeRoot "context.json"
    & java -jar $ResolvedJar explain --db $DbPath --format json "How are orders created and consumed?" |
        Set-Content -Encoding utf8 $ContextPath
    if ($LASTEXITCODE -ne 0) { throw "explain failed with exit code $LASTEXITCODE" }

    $Context = Get-Content -Raw $ContextPath
    if ($Context -notmatch '"observedEvidence"' -or $Context -notmatch 'orders.created') {
        throw "Explain smoke assertions failed."
    }
    Write-Output "Windows packaged smoke test passed."
}
finally {
    if (Test-Path $SmokeRoot) { Remove-Item -Recurse -Force $SmokeRoot }
}
