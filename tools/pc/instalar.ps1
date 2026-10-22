# Parche Español (España) para Pokémon Fire Ash (PC): añade el parche a mkxp.json
Set-Location -LiteralPath $PSScriptRoot
$p = 'mkxp.json'
if (-not (Test-Path $p)) {
  Write-Host 'No encuentro mkxp.json. Copia el contenido del parche en la carpeta del juego (donde está Game.exe) y vuelve a ejecutar INSTALAR.bat.'
  exit 1
}
$t = [IO.File]::ReadAllText((Resolve-Path $p))
if ($t -match 'ashdroid_patches\.rb') { Write-Host 'El parche ya estaba instalado.'; exit 0 }
if (-not (Test-Path 'mkxp.json.bak')) { Copy-Item $p 'mkxp.json.bak' }
$re = [regex]'(?m)^(\s*"preloadScript"\s*:\s*\[)'
if ($re.IsMatch($t)) {
  $t = $re.Replace($t, '$1"ashdroid_patches.rb", ', 1)
} else {
  $i = $t.IndexOf('{')
  $t = $t.Substring(0, $i + 1) + [Environment]::NewLine + '  "preloadScript": ["ashdroid_patches.rb"],' + $t.Substring($i + 1)
}
[IO.File]::WriteAllText((Resolve-Path $p), $t, (New-Object Text.UTF8Encoding $false))
Write-Host 'Parche instalado.'
