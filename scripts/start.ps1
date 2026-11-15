# TP4 - Demarrage de l'application dans le mode voulu.
# Usage :
#   .\scripts\start.ps1              # mode local (defaut, H2 memoire)
#   .\scripts\start.ps1 -Mode postgres     # infra Docker (postgres + pgAdmin) puis app
#   .\scripts\start.ps1 -Mode axonserver   # infra Docker (+ Axon Server) puis app
# L'application tourne en premier plan (Ctrl+C pour l'arreter). scripts\stop.ps1 arrete l'infra Docker.
[CmdletBinding()]
param(
    [ValidateSet('local', 'postgres', 'axonserver')]
    [string]$Mode = 'local'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Push-Location $root

try {
    # Maven doit tourner sur JDK 21 (le java systeme peut etre 17)
    if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path (Join-Path $env:JAVA_HOME 'bin') 'java.exe'))) {
        $env:JAVA_HOME = 'C:\Users\saada\.jdks\ms-21.0.11'
    }
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor DarkGray

    switch ($Mode) {
        'local' {
            Write-Host "== Mode local : event store + projections en H2 memoire, connecteurs Axon locaux ==" -ForegroundColor Cyan
            .\mvnw.cmd spring-boot:run
        }
        'postgres' {
            Write-Host "== Mode postgres : demarrage de l'infra Docker (postgres + pgAdmin) ==" -ForegroundColor Cyan
            docker compose up -d postgres pgadmin
            docker compose ps
            Write-Host "== Demarrage de l'application (profil postgres) ==" -ForegroundColor Cyan
            .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
        }
        'axonserver' {
            Write-Host "== Mode axonserver : demarrage de l'infra Docker (postgres + pgAdmin + Axon Server) ==" -ForegroundColor Cyan
            docker compose up -d postgres pgadmin axonserver
            docker compose ps
            Write-Host "== Demarrage de l'application (profil axonserver) ==" -ForegroundColor Cyan
            .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=axonserver"
        }
    }
}
finally {
    Pop-Location
}
