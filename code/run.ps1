# 编译并运行 GDUWS（须在 code/ 目录下运行以便定位 data/ 资源）
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $ScriptRoot "build.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Push-Location $ScriptRoot
try {
    . .\use-java13.ps1
    java -cp out com.gduws.Main
}
finally {
    Pop-Location
}
