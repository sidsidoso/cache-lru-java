# Compila o projeto para out/ e, opcionalmente, executa a demo ou os testes.
# Uso: .\build.ps1 [run|test]
param([string]$Acao = '')
$ErrorActionPreference = 'Stop'

$Main = 'br.com.sidsidoso.cache.Main'
$Testes = 'br.com.sidsidoso.cache.Testes'

if ($Acao -notin @('', 'run', 'test')) {
    Write-Host 'uso: .\build.ps1 [run|test]'
    exit 2
}

# Usa o JDK de JAVA_HOME, se definido; senao, o do PATH.
$javac = 'javac'
$java = 'java'
if ($env:JAVA_HOME) {
    $javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
}

Push-Location $PSScriptRoot
try {
    $dirOut = Join-Path $PSScriptRoot 'out'
    if (Test-Path $dirOut) {
        # o prefixo \\?\ contorna o limite de 260 caracteres (MAX_PATH) do Windows
        $alvo = if ($dirOut.StartsWith('\\')) { $dirOut } else { '\\?\' + $dirOut }
        [System.IO.Directory]::Delete($alvo, $true)
    }
    New-Item -ItemType Directory $dirOut | Out-Null

    $fontes = Get-ChildItem -Recurse -Filter *.java src\main\java, src\test\java | ForEach-Object { $_.FullName }
    & $javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d out $fontes
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    switch ($Acao) {
        'run'   { & $java -cp out $Main }
        'test'  { & $java -cp out $Testes }
        default { Write-Host 'Compilado em out/' }
    }
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
