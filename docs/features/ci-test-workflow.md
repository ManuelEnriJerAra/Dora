# CI Test Workflow

## Status

Implemented

## Feature

GitHub Actions runs `mvn test` on Java 25 for every pull request, every push to `master`, and on demand.

## Motivation

Tests were only run by hand, so a change could be merged without anyone noticing a broken test.

## Solution

- Added `.github/workflows/tests.yml`.
- The job runs on `windows-latest` (the platform Dora is mainly used on) and `ubuntu-latest`, with `fail-fast: false` so one platform failing does not hide the other result.
- Java 25 (Temurin) comes from `actions/setup-java`, which also caches `~/.m2`.
- `LANG`/`LC_ALL` are set to `C.UTF-8` because `UtilidadesTest` uses a non-ASCII folder name and fails under a non-UTF-8 file system encoding on Linux.
- Surefire already sets `java.awt.headless=true`, so no virtual display is needed.
- When a run fails, `target/surefire-reports/` is uploaded as an artifact for 7 days.
- A newer push to the same branch cancels the previous run.

## Files Changed

- `.github/workflows/tests.yml`
- `docs/features/ci-test-workflow.md`
- `docs/features/process/ci-test-workflow.md`

## Verification

The workflow runs on its own pull request; see the checks on that PR.

## Detailed Process

- `docs/features/process/ci-test-workflow.md`

## Follow-Up Notes

- If tests start needing network access or a real display, add it to the workflow rather than skipping the test.
- To make the checks required before merging, enable branch protection on `master` in the GitHub repository settings and pick both `mvn test` jobs.
- The `smoke` tag can be used for a faster job later with `-Dtest.tags=smoke`.

## Related Docs

- `AGENTS.md` (Build And Test)
