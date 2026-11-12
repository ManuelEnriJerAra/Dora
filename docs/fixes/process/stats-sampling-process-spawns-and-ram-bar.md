# Stats Sampling Spawns Processes Every Second And RAM Bar Does Not Reset Process

## Status

Fixed

## Linked Fix

- `docs/fixes/stats-sampling-process-spawns-and-ram-bar.md`

## Scope

Reduce external process launches from the stats sampler, measure the right process for script-launched servers, and reset the indicator bars when a server stops.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read the sampler | Traced `sampleActiveServers`, `readServerRamUsageMb`, `readServerProcessMetrics`, `readServerCpuUsagePercent` and `getLiveResourceSnapshot`. |
| DONE | 2. Measure the JVM | Added `resolveServerJvm` and used it for CPU, RAM and disk. |
| DONE | 3. Throttle external tools | 5-second per-process caches for RAM and Windows disk metrics; stop retrying `jstat` after 3 failures; stale cache entries pruned. |
| DONE | 4. Cheaper reads | `jstat` from Dora's own JDK; Linux RSS from `/proc`. |
| DONE | 5. Reset bars | `getLiveResourceSnapshot` returns zeros when the server is not running. |
| DONE | 6. Verify behavior | Added `PanelEstadisticasSamplingTest`. |

## Verification Notes

- `mvn -q -Dtest=PanelEstadisticasSamplingTest test`
- `mvn test` on Java 25.

The CPU savings on Windows were not measured in this change; check Task Manager with two running servers before and after.
