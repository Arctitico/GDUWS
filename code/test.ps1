# 编译并运行 GDUWS 的单元测试与集成测试（JUnit 5，无需 Maven/Gradle）
#
# 流程：编译主源码 -> out/，编译测试源码 -> out-test/，再用 JUnit Platform Console
# Standalone 扫描 out-test 执行全部 @Test。所有依赖 jar 均位于 libs/。
#
# 用法：
#   .\test.ps1                                           # 终端无界面运行全部测试（默认）
#   .\test.ps1 -Select com.gduws.model.PathfinderTest    # 仅运行某个测试类
#   .\test.ps1 -Visual                                   # 打开可视化场景回放器，亲眼看战斗推演/AI
param(
    [switch]$Visual,
    [string]$Select
)
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

# 定位 JDK
if (-not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $found = (Get-Command javac -ErrorAction SilentlyContinue).Source
    if ($found) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $found) }
    else { throw "未找到 JDK。请先安装：winget install EclipseAdoptium.Temurin.17.JDK" }
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Push-Location $ScriptRoot
try {
    $mainSrc = Join-Path $ScriptRoot "src\main\java"
    $testSrc = Join-Path $ScriptRoot "src\test\java"
    $outDir = Join-Path $ScriptRoot "out"
    $testOut = Join-Path $ScriptRoot "out-test"
    $libsDir = Join-Path $ScriptRoot "libs"

    foreach ($d in @($outDir, $testOut)) {
        if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d | Out-Null }
    }

    # libs 下全部 jar（含 JUnit console-standalone 与 jorbis/jogg）拼成类路径
    $libJars = Get-ChildItem -Path $libsDir -Filter *.jar | ForEach-Object { $_.FullName }
    $libsCp = $libJars -join ";"
    $junitJar = $libJars | Where-Object { $_ -match "junit-platform-console-standalone" } | Select-Object -First 1
    if (-not $junitJar) {
        throw "未找到 libs\junit-platform-console-standalone-*.jar，请将其放入 libs\ 目录。"
    }

    # 1) 编译主源码 -> out/
    $mainList = Join-Path $env:TEMP "gduws_main_sources.txt"
    $mainFiles = Get-ChildItem -Path $mainSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    [System.IO.File]::WriteAllLines($mainList, $mainFiles, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "编译主源码 $($mainFiles.Count) 个文件 ..."
    javac -encoding UTF-8 -cp $libsCp -d $outDir "@$mainList"
    if ($LASTEXITCODE -ne 0) { throw "主源码编译失败" }

    # 2) 编译测试源码 -> out-test/
    $testList = Join-Path $env:TEMP "gduws_test_sources.txt"
    $testFiles = Get-ChildItem -Path $testSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    [System.IO.File]::WriteAllLines($testList, $testFiles, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "编译测试源码 $($testFiles.Count) 个文件 ..."
    javac -encoding UTF-8 -cp "$outDir;$libsCp" -d $testOut "@$testList"
    if ($LASTEXITCODE -ne 0) { throw "测试源码编译失败" }

    $runtimeCp = "$testOut;$outDir;$libsCp"

    # 可视化模式：打开场景回放器，逐 tick 渲染战斗推演与 AI 行为（演示用，断言仍以 JUnit 为准）
    if ($Visual) {
        Write-Host "启动可视化场景回放器 ..."
        java -cp $runtimeCp com.gduws.testkit.ScenarioViewer
        exit $LASTEXITCODE
    }

    # 默认：无界面运行 JUnit，终端输出测试结果（仅扫描 out-test，out/ 与 libs 提供链接依赖）
    # 默认扫描过滤器只匹配 *Test，需追加 *IT 以纳入 integration 包下的集成测试
    $includePattern = '^(Test.*|.+[.$]Test.*|.*Tests?|.*TestCase|.*IT)$'
    Write-Host "运行测试 ..."
    if ($Select) {
        java -jar $junitJar execute -cp $runtimeCp --select-class=$Select --details=tree
    } else {
        java -jar $junitJar execute -cp $runtimeCp --scan-class-path=$testOut `
            --include-classname=$includePattern --details=tree
    }
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
