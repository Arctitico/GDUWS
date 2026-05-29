# 编译 GDUWS 全部 Java 源码到 out/ 目录
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $ScriptRoot
try {
    . .\use-java13.ps1

    $srcRoot = Join-Path $ScriptRoot "src\main\java"
    $outDir = Join-Path $ScriptRoot "out"
    if (-not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir | Out-Null
    }

    $sources = Get-ChildItem -Path $srcRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    $listFile = Join-Path $env:TEMP "gduws_sources.txt"
    [System.IO.File]::WriteAllLines($listFile, $sources, (New-Object System.Text.UTF8Encoding($false)))

    Write-Host "编译 $($sources.Count) 个源文件..."
    javac -encoding UTF-8 -d $outDir "@$listFile"
    if ($LASTEXITCODE -ne 0) {
        throw "编译失败 (exit $LASTEXITCODE)"
    }
    Write-Host "编译成功 -> $outDir"
}
finally {
    Pop-Location
}
