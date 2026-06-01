# 构建 GDUWS 可在 Windows 直接游玩的 exe 发行包
# 产物：code\dist\GDUWS.exe（原生启动器）+ jlink 最小运行时 + GDUWS.jar + 资源
# 用法：
#   .\build.ps1            常规构建（已存在的 runtime 不重复生成，加快增量构建）
#   .\build.ps1 -Clean     清空 dist 后全量重建（重新用 jlink 生成运行时）
param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

# Locate JDK
if (-not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $found = (Get-Command javac -ErrorAction SilentlyContinue).Source
    if ($found) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $found) }
    else { throw "JDK not found. Run: winget install EclipseAdoptium.Temurin.17.JDK" }
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$JavaBin = Join-Path $env:JAVA_HOME "bin"

Push-Location $ScriptRoot
try {
    $outDir = Join-Path $ScriptRoot "out"
    $distDir = Join-Path $ScriptRoot "dist"
    $runtimeDir = Join-Path $distDir "runtime"

    # 1) 编译源码 -> out/
    & (Join-Path $ScriptRoot "compile.ps1")
    if ($LASTEXITCODE -ne 0) { throw "源码编译失败" }

    # 2) 准备 dist 目录
    if ($Clean -and (Test-Path $distDir)) {
        Write-Host "清空 dist ..."
        Remove-Item $distDir -Recurse -Force
    }
    if (-not (Test-Path $distDir)) {
        New-Item -ItemType Directory -Path $distDir | Out-Null
    }

    # 3) 将第三方依赖（jorbis/jogg）解包进 out/，并入单一可运行 JAR
    $libsDir = Join-Path $ScriptRoot "libs"
    if (Test-Path $libsDir) {
        Push-Location $outDir
        try {
            foreach ($lib in Get-ChildItem -Path $libsDir -Filter *.jar) {
                Write-Host "并入依赖 $($lib.Name) ..."
                jar xf $lib.FullName
                if ($LASTEXITCODE -ne 0) { throw "解包依赖失败：$($lib.Name)" }
            }
        }
        finally {
            Pop-Location
        }
        # 移除依赖自带的 META-INF，避免与下方生成的清单冲突
        $libMeta = Join-Path $outDir "META-INF"
        if (Test-Path $libMeta) { Remove-Item $libMeta -Recurse -Force }
    }

    # 4) 打包可运行 JAR（含 Main-Class）
    $manifest = Join-Path $env:TEMP "gduws_manifest.txt"
    Set-Content -Path $manifest -Value "Main-Class: com.gduws.Main`n" -Encoding ascii
    $jarPath = Join-Path $distDir "GDUWS.jar"
    Write-Host "打包 GDUWS.jar ..."
    jar cfm $jarPath $manifest -C $outDir .
    if ($LASTEXITCODE -ne 0) { throw "JAR 打包失败" }

    # 5) 拷贝运行期资源（相对路径 data/ 与 assets/）
    Write-Host "拷贝资源 data/ assets/ ..."
    foreach ($res in @("data", "assets")) {
        $src = Join-Path $ScriptRoot $res
        $dst = Join-Path $distDir $res
        if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
        if (Test-Path $src) { Copy-Item $src $dst -Recurse }
    }

    # 6) 用 csc.exe 编译原生启动器 -> dist\GDUWS.exe
    $csc = Get-ChildItem "C:\Windows\Microsoft.NET\Framework64" -Filter csc.exe -Recurse |
        Sort-Object FullName | Select-Object -Last 1
    if (-not $csc) { throw "未找到 csc.exe（.NET Framework 编译器）" }
    $launcherSrc = Join-Path $ScriptRoot "launcher\Launcher.cs"
    $exePath = Join-Path $distDir "GDUWS.exe"
    Write-Host "编译启动器 GDUWS.exe ..."
    & $csc.FullName /nologo /target:winexe /optimize+ `
        /reference:System.Windows.Forms.dll `
        "/out:$exePath" $launcherSrc
    if ($LASTEXITCODE -ne 0) { throw "启动器编译失败" }

    # 7) 用 jlink 生成最小运行时（约 40-50 MB，远小于完整 JDK）
    if ($Clean -and (Test-Path $runtimeDir)) {
        Write-Host "清空旧 runtime ..."
        Remove-Item $runtimeDir -Recurse -Force
    }
    if (-not (Test-Path $runtimeDir)) {
        $jlink = Join-Path $JavaBin "jlink.exe"
        if (-not (Test-Path $jlink)) {
            throw "未找到 jlink.exe，请安装完整 JDK 17+（Temurin 等），仅 JRE 不够"
        }
        $jmodsDir = Join-Path $env:JAVA_HOME "jmods"
        if (-not (Test-Path $jmodsDir) -or (Get-ChildItem $jmodsDir -Filter *.jmod).Count -eq 0) {
            throw "JDK 缺少 jmods 模块文件，请安装完整 JDK 而非精简版"
        }
        Write-Host "jlink 生成最小运行时 -> dist\runtime ..."
        & $jlink --add-modules java.desktop `
            --output $runtimeDir `
            --strip-debug --no-man-pages --no-header-files `
            --compress=2
        if ($LASTEXITCODE -ne 0) { throw "jlink 生成运行时失败" }
    } else {
        Write-Host "已存在 dist\runtime，跳过（如需更新请用 -Clean）"
    }

    Write-Host ""
    Write-Host "构建完成：$exePath"
    Write-Host "双击该 exe 即可在 Windows 上游玩。"
}
finally {
    Pop-Location
}
