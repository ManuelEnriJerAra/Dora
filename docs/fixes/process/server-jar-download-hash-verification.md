# Server Jar Downloads Are Not Verified Process

## Status

Fixed (Vanilla, Paper, Purpur)

## Linked Fix

- `docs/fixes/server-jar-download-hash-verification.md`

## Scope

Check downloaded server jars against provider hashes where the provider already publishes one.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Read `platform-adapters-pipeline.md` and every `downloader().download(...)` call. |
| DONE | 2. Shared check | Added `DownloadChecksum.verifyOrDelete`. |
| DONE | 3. Vanilla | Cache Mojang's server `sha1` with the URL and verify it. |
| DONE | 4. Paper | Read `checksums.sha256` from Fill builds and verify it. |
| DONE | 5. Purpur | Read the build `md5` and verify it; skip when the lookup fails. |
| DONE | 6. Verify behavior | Added Paper and Purpur match/mismatch tests in `ServerPlatformAdaptersTest`. |
| TO DO | 7. Forge/NeoForge/Quilt installers | Optional: fetch the Maven `.sha1` side file. Fabric publishes no hash. |

## Verification Notes

- `mvn -q -Dtest=ServerPlatformAdaptersTest test`
- `mvn test` on Java 25.

Real downloads were not exercised because the build environment blocks Mojang, PaperMC and Purpur.
