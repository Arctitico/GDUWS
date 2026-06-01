# 从源码编译并直接运行 GDUWS（开发调试用，须在 code/ 目录下运行以便定位 data/ 资源）
# 若需生成可分发的 exe 发行包，请改用 build.ps1
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $ScriptRoot "compile.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Push-Location $ScriptRoot
try {
    # Locate JDK (compile.ps1 may have set JAVA_HOME, but ensure PATH is set)
    if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        $found = (Get-Command java -ErrorAction SilentlyContinue).Source
        if ($found) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $found) }
        else { throw "JDK not found. Run: winget install EclipseAdoptium.Temurin.17.JDK" }
    }
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
    java -cp "out;libs/*" com.gduws.Main
}
finally {
    Pop-Location
}
