# TP4 - Arret de l'infrastructure Docker.
# Usage :
#   .\scripts\stop.ps1                # arrete les conteneurs
#   .\scripts\stop.ps1 -RemoveVolumes # arrete ET supprime conteneurs + volumes (donnees perdues)
# Note : l'application demarree via scripts\start.ps1 tourne au premier plan : l'arreter avec Ctrl+C.
[CmdletBinding()]
param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Push-Location $root

try {
    Write-Host "== Arret de l'infrastructure Docker (compose) ==" -ForegroundColor Cyan
    docker compose stop
    if ($RemoveVolumes) {
        Write-Host "== Suppression des conteneurs et volumes ==" -ForegroundColor Yellow
        docker compose down -v
    }
    docker compose ps
}
finally {
    Pop-Location
}
