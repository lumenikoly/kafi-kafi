param(
    [Parameter(Mandatory=$true)][string]$Executable,
    [string]$Output='desktop-measurement.json',
    [int]$IdleSeconds=15,
    [int]$StartupTimeoutSeconds=30
)
$executablePath=(Resolve-Path -LiteralPath $Executable).Path
$temporaryOutput=Join-Path ([IO.Path]::GetTempPath()) ("kafi-benchmark-"+[Guid]::NewGuid().ToString()+'.log')
$temporaryError=$temporaryOutput+'.stderr'
$watch=[Diagnostics.Stopwatch]::StartNew()
$process=Start-Process -FilePath $executablePath -PassThru -WindowStyle Hidden -RedirectStandardOutput $temporaryOutput -RedirectStandardError $temporaryError
try {
    $interactiveMs=$null
    while ($watch.Elapsed.TotalSeconds -lt $StartupTimeoutSeconds) {
        $process.Refresh()
        if ($process.HasExited) {throw 'Application exited before the interactive marker.'}
        $log=Get-Content -LiteralPath $temporaryOutput -Raw -ErrorAction SilentlyContinue
        if ($log -match 'startup_ms=(\d+)') {$interactiveMs=[long]$Matches[1];break}
        Start-Sleep -Milliseconds 25
    }
    if ($null -eq $interactiveMs) {throw 'No ui_ready marker received. Use a Tauri executable with startup tracing.'}
    Start-Sleep -Seconds $IdleSeconds
    $inventory=Get-CimInstance Win32_Process
    $processIds=[Collections.Generic.HashSet[int]]::new()
    [void]$processIds.Add($process.Id)
    do {
        $added=$false
        foreach ($candidate in $inventory) {
            if ($processIds.Contains([int]$candidate.ParentProcessId) -and $processIds.Add([int]$candidate.ProcessId)) {$added=$true}
        }
    } while ($added)
    $tree=foreach ($processId in $processIds) {Get-Process -Id $processId -ErrorAction SilentlyContinue}
    $measurement=[ordered]@{
        runtime='Tauri';os=[Environment]::OSVersion.VersionString;architecture=[Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
        interactiveMs=$interactiveMs;startupMethod='Rust process start to React requestAnimationFrame / ui_ready'
        idleWorkingSetBytes=($tree|Measure-Object WorkingSet64 -Sum).Sum
        processCount=@($tree).Count;executableBytes=(Get-Item -LiteralPath $executablePath).Length
        idleSeconds=$IdleSeconds;measuredAtUtc=[DateTime]::UtcNow.ToString('o')
    }
    $measurement|ConvertTo-Json|Set-Content -LiteralPath $Output
    $measurement|ConvertTo-Json
} finally {
    # Only processes belonging to the executable launched for this measurement.
    Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
}
