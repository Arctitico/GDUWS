# 构建 GDUWS 可在 Windows 直接游玩的 exe 发行包
# 产物：code\dist\GDUWS.exe（原生启动器）+ 内置 JRE + GDUWS.jar + 资源
# 用法：
#   .\build.ps1            常规构建（已存在的 runtime 不重复拷贝，加快增量构建）
#   .\build.ps1 -Clean     清空 dist 后全量重建（重新拷贝内置 JRE）
param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $ScriptRoot
try {
    . .\use-java13.ps1

    $outDir = Join-Path $ScriptRoot "out"
    $distDir = Join-Path $ScriptRoot "dist"
    $runtimeDir = Join-Path $distDir "runtime"
    $jvmSource = $env:JAVA_HOME

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

    # 7) 拷贝内置 JRE（体积较大，已存在则跳过，可用 -Clean 强制重建）
    if (-not (Test-Path $runtimeDir)) {
        Write-Host "拷贝内置 JRE -> dist\runtime（首次较慢）..."
        Copy-Item $jvmSource $runtimeDir -Recurse
    } else {
        Write-Host "已存在 dist\runtime，跳过拷贝（如需更新请用 -Clean）"
    }

    Write-Host ""
    Write-Host "构建完成：$exePath"
    Write-Host "双击该 exe 即可在 Windows 上游玩。"
}
finally {
    Pop-Location
}
