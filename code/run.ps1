# 从源码编译并直接运行 GDUWS（开发调试用，须在 code/ 目录下运行以便定位 data/ 资源）
# 若需生成可分发的 exe 发行包，请改用 build.ps1
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $ScriptRoot "compile.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Push-Location $ScriptRoot
try {
    . .\use-java13.ps1
    java -cp out com.gduws.Main
}
finally {
    Pop-Location
}
