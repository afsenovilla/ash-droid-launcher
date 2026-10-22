# Quita el parche de mkxp.json
Set-Location -LiteralPath $PSScriptRoot
$p = 'mkxp.json'
$t = [IO.File]::ReadAllText((Resolve-Path $p))
$t = [regex]::Replace($t, '(?m)^\s*"preloadScript"\s*:\s*\[\s*"ashdroid_patches\.rb"\s*\],?\r?\n', '')
$t = $t -replace '"ashdroid_patches\.rb",\s*', '' -replace ',\s*"ashdroid_patches\.rb"', ''
[IO.File]::WriteAllText((Resolve-Path $p), $t, (New-Object Text.UTF8Encoding $false))
Write-Host 'Parche desinstalado. Puedes borrar los archivos que empiezan por "ashdroid".'
