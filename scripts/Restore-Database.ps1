#requires -Version 5.1
param([Parameter(Mandatory=$true)][string]$BackupPath, [string]$Database = ('kynex_restore_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + [Guid]::NewGuid().ToString('N').Substring(0,8)), [string]$ProjectName, [string]$EnvironmentFile)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Database.Common.ps1')
Assert-KynexDatabaseName $Database
if (!$Database.StartsWith('kynex_restore_')) { throw 'El destino debe ser una base nueva cuyo nombre comience por kynex_restore_.' }
$archivePath = (Resolve-Path -LiteralPath $BackupPath -ErrorAction Stop).Path
$metadataPath = $archivePath + '.json'
if (!(Test-Path -LiteralPath $metadataPath -PathType Leaf)) { throw 'Falta el archivo .dump.json con la verificacion SHA-256.' }
$metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($metadata.formatVersion -ne 1 -or $metadata.application -ne 'KYNEX' -or $metadata.sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'Los metadatos del respaldo no son validos.' }
$archive = Get-Item -LiteralPath $archivePath
if ($archive.Length -ne $metadata.archiveBytes -or (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $metadata.sha256) { throw 'El respaldo no coincide con su SHA-256 o su tamano. No se creara una base.' }
$context = Get-KynexDatabaseContext -ProjectName $ProjectName -EnvironmentFile $EnvironmentFile
if ($Database -eq $context.Database -or $Database -eq $metadata.sourceDatabase) { throw 'El destino no puede ser la base de origen.' }
$exists = (Invoke-KynexSql $context 'postgres' ("select count(*) from pg_database where datname='$Database';")).Trim()
if ($exists -ne '0') { throw 'La base de destino ya existe. No se sobrescribira ni se eliminara.' }
$temporaryPath = '/tmp/kynex_' + [Guid]::NewGuid().ToString('N') + '.dump'
try {
    Invoke-KynexDocker $context @('cp',$archivePath,('postgres:' + $temporaryPath)) 'No fue posible preparar el archivo de restauracion.' | Out-Null
    Invoke-KynexDocker $context @('exec','-T','postgres','pg_restore','--list',$temporaryPath) 'El respaldo no tiene un catalogo valido.' | Out-Null
    # createdb tambien rechaza una base que aparezca entre la comprobacion y este paso.
    Invoke-KynexDocker $context @('exec','-T','postgres','createdb','-U',$context.User,'--template=template0','--encoding=UTF8',$Database) 'No fue posible crear una base nueva.' | Out-Null
    Invoke-KynexDocker $context @('exec','-T','postgres','pg_restore','-U',$context.User,'--dbname',$Database,'--no-owner','--no-privileges','--single-transaction','--exit-on-error',$temporaryPath) 'La restauracion fallo. La base nueva se conserva para diagnostico; la original no se modifica.' | Out-Null
    $sessionTable = (Invoke-KynexSql $context $Database "select coalesce(to_regclass('public.refresh_session')::text,'');").Trim()
    if ($sessionTable -ne 'refresh_session' -and $sessionTable -ne 'public.refresh_session') { throw 'La copia no contiene las sesiones esperadas de KYNEX. No se declara la restauracion completada.' }
    Invoke-KynexSql $context $Database 'update public.refresh_session set revoked=true where not revoked;' | Out-Null
    $remaining = (Invoke-KynexSql $context $Database 'select count(*) from public.refresh_session where not revoked;').Trim()
    if ($remaining -ne '0') { throw 'No fue posible revocar las sesiones de la copia restaurada.' }
    Write-Host 'Restauracion completada en una base nueva. Las cuentas conservan sus datos y deben iniciar sesion otra vez.'
    [pscustomobject]@{ Database=$Database; SourceDatabase=$metadata.sourceDatabase; BackupPath=$archivePath; SessionsRevoked=$true }
} finally { Remove-KynexContainerFile $context $temporaryPath }
