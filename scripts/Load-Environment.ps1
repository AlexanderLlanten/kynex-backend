# Ejecutar mediante . .\scripts\Load-Environment.ps1 para conservar las variables en la sesión.
param([string]$Path = '.env')
$ErrorActionPreference = 'Stop'
foreach ($line in Get-Content -LiteralPath $Path) {
    $line = $line.Trim()
    if (!$line -or $line.StartsWith('#')) { continue }
    $parts = $line.Split('=', 2)
    if ($parts.Count -ne 2 -or $parts[0] -notmatch '^[A-Z][A-Z0-9_]*$') { throw 'Una variable de .env tiene formato incorrecto.' }
    [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
}
