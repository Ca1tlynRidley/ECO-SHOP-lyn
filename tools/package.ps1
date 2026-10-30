$ErrorActionPreference = 'Stop'
$shopRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$shopDist = Join-Path $shopRoot 'dist\1.3.0'
$bundleRoot = Join-Path $shopRoot 'build\install-bundle-1.3.0'
$bundleMods = Join-Path $bundleRoot 'mods'
$bundleConfig = Join-Path $bundleRoot 'config\shop-mod-lyn'
$bundleDocs = Join-Path $bundleRoot 'docs'
New-Item -ItemType Directory -Path $shopDist,$bundleMods,$bundleConfig,$bundleDocs -Force | Out-Null
$shopJar = Join-Path $shopRoot 'build\libs\SHOP-MOD-lyn-1.3.0.jar'
Copy-Item -LiteralPath $shopJar -Destination $shopDist -Force
Copy-Item -LiteralPath $shopJar -Destination $bundleMods -Force
Copy-Item -LiteralPath (Join-Path $shopRoot 'config\prices.json') -Destination $bundleConfig -Force
Copy-Item -LiteralPath (Join-Path $shopRoot 'config\auctions.json') -Destination $bundleConfig -Force
Copy-Item -LiteralPath (Join-Path $shopRoot 'docs\AUCTIONS.md') -Destination $bundleDocs -Force
foreach($document in @('README.md','SMOKE-TEST.md','LICENSE')) {
    Copy-Item -LiteralPath (Join-Path $shopRoot $document) -Destination $bundleRoot -Force
}
Get-FileHash -Algorithm SHA256 -LiteralPath $shopJar | ForEach-Object { $_.Hash.ToLowerInvariant() + '  SHOP-MOD-lyn-1.3.0.jar' } | Set-Content -LiteralPath (Join-Path $shopDist 'SHA256SUMS.txt') -Encoding ASCII
Compress-Archive -LiteralPath $bundleMods,(Join-Path $bundleRoot 'config'),$bundleDocs,(Join-Path $bundleRoot 'README.md'),(Join-Path $bundleRoot 'SMOKE-TEST.md'),(Join-Path $bundleRoot 'LICENSE') -DestinationPath (Join-Path $shopDist 'SHOP-MOD-lyn-1.3.0.zip') -Force
Write-Output ('Install files: ' + $shopDist)
