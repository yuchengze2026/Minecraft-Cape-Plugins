param([switch]$Offline, [switch]$Clean)
$ErrorActionPreference = 'Stop'
$buildRoot = $PSScriptRoot
$workspaceRoot = Split-Path $buildRoot -Parent
$localMaven = Join-Path $workspaceRoot 'work/tools/apache-maven-3.9.9/bin/mvn.cmd'
$mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
if ($mavenCommand) { $maven = $mavenCommand.Source }
elseif (Test-Path -LiteralPath $localMaven) { $maven = $localMaven }
else { throw '请安装 Maven 3.9+ 并加入 PATH，同时配置 JDK 21 的 JAVA_HOME。' }
$temp = Join-Path $buildRoot 'tmp'
New-Item -ItemType Directory -Force $temp | Out-Null
$buildArgs = @('-f', (Join-Path $buildRoot 'pom.xml'), "-DargLine=-Djava.io.tmpdir=$temp")
$localRepository = Join-Path $workspaceRoot 'work/m2'
if (Test-Path -LiteralPath $localRepository) { $buildArgs += "-Dmaven.repo.local=$localRepository" }
if ($Offline) { $buildArgs += '-o' }
if ($Clean) { $buildArgs += 'clean' }
$buildArgs += 'verify'
& $maven @buildArgs
exit $LASTEXITCODE
