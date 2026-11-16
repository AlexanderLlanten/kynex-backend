# Funciones compartidas por las herramientas de copia y restauracion. Compatible con PowerShell 5.1.
function Assert-KynexDatabaseName {
    param([string]$Name)
    if ($Name -cnotmatch '^[a-z][a-z0-9_]{0,62}$') { throw 'El nombre de la base debe usar letras minusculas, numeros y guion bajo, hasta 63 caracteres.' }
}

function Invoke-KynexDocker {
    param($Context, [string[]]$CommandArguments, [string]$FailureMessage = 'Fallo una operacion de PostgreSQL.')
    $dockerArguments = $Context.Arguments
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $nativeOutput = & docker @dockerArguments @CommandArguments 2>&1
        $commandStatus = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($commandStatus -ne 0) { throw ($FailureMessage + [Environment]::NewLine + ($nativeOutput -join [Environment]::NewLine)) }
    return @($nativeOutput | ForEach-Object { $_.ToString() })
}

function Get-KynexDatabaseContext {
    param([string]$ProjectName, [string]$EnvironmentFile)
    if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Abre Docker Desktop e instala Docker Compose antes de continuar.' }
    $projectDirectory = Split-Path -Parent $PSScriptRoot
    $composePath = Join-Path $projectDirectory 'compose.yml'
    $composeArguments = @('compose', '--project-directory', $projectDirectory, '--file', $composePath)
    if ($EnvironmentFile) {
        $environmentPath = (Resolve-Path -LiteralPath $EnvironmentFile -ErrorAction Stop).Path
        $composeArguments += @('--env-file', $environmentPath)
    }
    if ($ProjectName) {
        if ($ProjectName -notmatch '^[a-z0-9][a-z0-9_-]*$') { throw 'El nombre del proyecto Docker no es valido.' }
        $composeArguments += @('--project-name', $ProjectName)
    }
    $context = [pscustomobject]@{ Arguments = $composeArguments; ProjectDirectory = $projectDirectory; User = ''; Database = '' }
    $settings = @(Invoke-KynexDocker $context @('exec', '-T', 'postgres', 'printenv', 'POSTGRES_USER', 'POSTGRES_DB') 'PostgreSQL debe estar iniciado con docker compose up -d postgres.')
    if ($settings.Count -ne 2) { throw 'No fue posible leer el usuario y la base del contenedor PostgreSQL.' }
    Assert-KynexDatabaseName $settings[0]; Assert-KynexDatabaseName $settings[1]
    $context.User = $settings[0]; $context.Database = $settings[1]
    return $context
}

function Invoke-KynexSql {
    param($Context, [string]$Database, [string]$Sql)
    Assert-KynexDatabaseName $Database
    return (Invoke-KynexDocker $Context @('exec', '-T', 'postgres', 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', $Context.User, '-d', $Database, '-c', $Sql)) -join "`n"
}

function Remove-KynexContainerFile {
    param($Context, [string]$Path)
    if ($Path -notmatch '^/tmp/kynex_[a-f0-9]{32}\.(dump|sql)$') { throw 'Ruta temporal no reconocida.' }
    try { Invoke-KynexDocker $Context @('exec', '-T', 'postgres', 'rm', '-f', '--', $Path) | Out-Null } catch { Write-Warning 'No se pudo retirar un archivo temporal del contenedor.' }
}
