# Stats Sampling Spawns Processes Every Second And RAM Bar Does Not Reset

## Status

Fixed

## Original Issue

- The background stats sampler launched external tools for every running server every second: `jstat` (a whole JVM), plus `powershell` with `Get-CimInstance` on Windows, and `ps` on Linux/macOS. With a couple of servers this used noticeable CPU on its own, and the 900 ms PowerShell timeout could make the 1-second schedule fall behind.
- On Forge/NeoForge (started through `run.bat`/`run.sh`) CPU, RAM and disk were measured on `cmd.exe`/`sh` instead of the Java server, so CPU stayed near 0% and `jstat` always failed (adding a fallback spawn every second).
- The CPU/RAM/disk indicator bars kept showing the last reading after a server stopped, including RAM values loaded from a previous session's history.

## Root Cause

`PanelEstadisticas.sampleActiveServers` called `readServerRamUsageMb` and `readServerProcessMetrics` on every tick with no caching, always using `Process.pid()` of the launcher. `getLiveResourceSnapshot` returned the history's last values regardless of whether the server was running.

## Solution

- `resolveServerJvm(Process)` picks the Java child when the launcher is a script, and CPU, RAM and disk are read from it.
- RAM (jstat/PowerShell/ps) and Windows disk metrics are cached per process for 5 seconds (`EXPENSIVE_SAMPLE_INTERVAL_MS`). CPU stays at 1 second because it is read in-process from `ProcessHandle`.
- After 3 consecutive `jstat` failures for a process, Dora stops launching it and uses the OS memory reading.
- `jstat` is taken from the JDK Dora runs on when available, instead of whatever is on PATH.
- On Linux, resident memory is read from `/proc/<pid>/status` without launching `ps`.
- `getLiveResourceSnapshot` returns zeros for a stopped server.

## Files Changed

- `src/main/java/vista/PanelEstadisticas.java`
- `src/test/java/vista/PanelEstadisticasSamplingTest.java`

## Verification

- `mvn -q -Dtest=PanelEstadisticasSamplingTest test`
- `mvn test`

## Detailed Process

- `docs/fixes/process/stats-sampling-process-spawns-and-ram-bar.md`

## Regression Notes

Anything that launches an external process from the sampler must be throttled. Always measure the server JVM, not the launcher script.
