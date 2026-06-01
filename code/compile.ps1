# 编译 GDUWS 全部 Java 源码到 out/ 目录（供 build.ps1 / run.ps1 复用）
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

# Locate JDK
if (-not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $found = (Get-Command javac -ErrorAction SilentlyContinue).Source
    if ($found) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $found) }
    else { throw "JDK not found. Run: winget install EclipseAdoptium.Temurin.17.JDK" }
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Push-Location $ScriptRoot
try {
    $srcRoot = Join-Path $ScriptRoot "src\main\java"
    $outDir = Join-Path $ScriptRoot "out"
    if (-not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir | Out-Null
    }

    $sources = Get-ChildItem -Path $srcRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    $listFile = Join-Path $env:TEMP "gduws_sources.txt"
    [System.IO.File]::WriteAllLines($listFile, $sources, (New-Object System.Text.UTF8Encoding($false)))

    # 第三方依赖（OGG 解码 jorbis/jogg）加入编译类路径
    $libsDir = Join-Path $ScriptRoot "libs"
    $cp = (Get-ChildItem -Path $libsDir -Filter *.jar -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName }) -join ";"

    Write-Host "编译 $($sources.Count) 个源文件..."
    if ($cp) {
        javac -encoding UTF-8 -cp $cp -d $outDir "@$listFile"
    } else {
        javac -encoding UTF-8 -d $outDir "@$listFile"
    }
    if ($LASTEXITCODE -ne 0) {
        throw "编译失败 (exit $LASTEXITCODE)"
    }
    Write-Host "编译成功 -> $outDir"
}
finally {
    Pop-Location
}
