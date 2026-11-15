# TP4 - Verification complete : build + tests unitaires/integration + validation du compose.
# Usage : .\scripts\verify.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Push-Location $root

try {
    # Maven doit tourner sur JDK 21 (le java systeme peut etre 17)
    if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path (Join-Path $env:JAVA_HOME 'bin') 'java.exe'))) {
        $env:JAVA_HOME = 'C:\Users\saada\.jdks\ms-21.0.11'
    }
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"

    Write-Host "== 1/3 Build + tests (mvnw clean verify) ==" -ForegroundColor Cyan
    .\mvnw.cmd clean verify
    if ($LASTEXITCODE -ne 0) { throw "mvnw clean verify a echoue (code $LASTEXITCODE)" }

    Write-Host "== 2/3 Validation docker compose config ==" -ForegroundColor Cyan
    docker compose config -q
    if ($LASTEXITCODE -ne 0) { throw "docker compose config a echoue (code $LASTEXITCODE)" }
    Write-Host "compose.yaml : valide" -ForegroundColor Green

    Write-Host "== 3/3 Verification OK ==" -ForegroundColor Green
    Write-Host "Build vert + compose valide. Le jar est disponible dans target\."
    Write-Host "Pour une image Docker de l'application : .\mvnw.cmd clean package puis docker compose --profile full up -d --build"
}
finally {
    Pop-Location
}
