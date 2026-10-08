param([string]$MavenRepository = "$env:USERPROFILE\.m2\repository")

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'run-phase1.ps1') -MavenRepository $MavenRepository -TestSelector 'PhaseOne*Test,ReauditTradeTest,PhaseTwo*Test'
if ($LASTEXITCODE -ne 0) { throw 'Phase-two verification failed.' }
