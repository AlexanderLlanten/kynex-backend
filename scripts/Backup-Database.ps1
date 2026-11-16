#requires -Version 5.1
param([string]$OutputDirectory, [string]$Database, [string]$ProjectName, [string]$EnvironmentFile)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Database.Common.ps1')
$context = Get-KynexDatabaseContext -ProjectName $ProjectName -EnvironmentFile $EnvironmentFile
if (!$Database) { $Database = $context.Database }
Assert-KynexDatabaseName $Database
if (!$OutputDirectory) { $OutputDirectory = Join-Path $context.ProjectDirectory '.local/backups' }
$directory = [IO.Path]::GetFullPath((Join-Path $OutputDirectory '.'))
if ($directory.Length -gt [IO.Path]::GetPathRoot($directory).Length) { $directory = $directory.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) }
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$identifier = [Guid]::NewGuid().ToString('N')
$fileName = $Database + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $identifier.Substring(0,8) + '.dump'
$backupPath = Join-Path $directory $fileName
$partialPath = $backupPath + '.partial'
$metadataPath = $backupPath + '.json'
foreach ($path in @($backupPath, $partialPath, $metadataPath)) {
    if (Test-Path -LiteralPath $path) { throw 'El archivo de salida ya existe; no se sobrescribira.' }
    if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($path)) -ne $directory) { throw 'La salida debe permanecer dentro de la carpeta elegida.' }
}
$temporaryPath = '/tmp/kynex_' + $identifier + '.dump'
try {
    Invoke-KynexDocker $context @('exec','-T','postgres','pg_dump','-U',$context.User,'-d',$Database,'--format=custom','--file',$temporaryPath) 'No fue posible crear la copia de seguridad.' | Out-Null
    Invoke-KynexDocker $context @('exec','-T','postgres','pg_restore','--list',$temporaryPath) 'El archivo generado no es un respaldo valido.' | Out-Null
    # Docker copia el archivo binario: no pasa por la redireccion de texto de PowerShell.
    Invoke-KynexDocker $context @('cp',('postgres:' + $temporaryPath),$partialPath) 'No fue posible copiar el respaldo al equipo.' | Out-Null
    $file = Get-Item -LiteralPath $partialPath
    if ($file.Length -lt 5) { throw 'El respaldo esta vacio o incompleto.' }
    $hash = (Get-FileHash -LiteralPath $partialPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $serverVersion = (Invoke-KynexSql $context $Database 'show server_version;').Trim()
    Move-Item -LiteralPath $partialPath -Destination $backupPath -ErrorAction Stop
    $metadata = [ordered]@{ formatVersion=1; application='KYNEX'; createdUtc=[DateTime]::UtcNow.ToString('o'); sourceDatabase=$Database; postgresVersion=$serverVersion; archiveFile=$fileName; archiveBytes=$file.Length; sha256=$hash }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding UTF8
    Write-Host 'Copia de seguridad creada y catalogo verificado. Conserva el archivo .dump junto a su .json.'
    [pscustomobject]@{ BackupPath=$backupPath; MetadataPath=$metadataPath; SourceDatabase=$Database; Sha256=$hash; Bytes=$file.Length }
} finally { Remove-KynexContainerFile $context $temporaryPath }
