# Dot-source this file on a Windows development machine before cargo / pnpm tauri.
$vsDiscovery = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (Test-Path -LiteralPath $vsDiscovery) {
    $vsInstall = & $vsDiscovery -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    $vsBatch = Join-Path $vsInstall 'Common7\Tools\VsDevCmd.bat'
    $vsVariables = & cmd.exe /d /s /c "`"$vsBatch`" -arch=x64 -host_arch=x64 >nul && set"
    foreach ($vsLine in $vsVariables) {
        if ($vsLine -match '^([^=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
    }
}
$nativePerlCandidates = @(
    (Join-Path $PSScriptRoot '..\.tmp\toolchain\strawberry-perl\perl\bin'),
    'C:\Strawberry\perl\bin'
)
foreach ($nativePerl in $nativePerlCandidates) {
    if (Test-Path -LiteralPath (Join-Path $nativePerl 'perl.exe')) { $env:PATH = "$nativePerl;$env:PATH"; break }
}
$nativeCmake = Join-Path $env:ProgramFiles 'CMake\bin'
if (Test-Path -LiteralPath (Join-Path $nativeCmake 'cmake.exe')) { $env:PATH = "$nativeCmake;$env:PATH" }
