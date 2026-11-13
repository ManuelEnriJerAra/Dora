# CI Test Workflow Process

## Status

Done

## Linked Feature

- `docs/features/ci-test-workflow.md`

## Scope

Run the existing test suite automatically on GitHub. No test or production code changes.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Java 25, Maven, Surefire already forces headless AWT; NBT comes from JitPack. |
| DONE | 2. Add workflow | `.github/workflows/tests.yml` with a Windows + Linux matrix. |
| DONE | 3. Verify | Checked by the workflow's own run on its pull request. |

## Implementation Notes

- Earlier local runs on Linux showed two `UtilidadesTest` cases fail unless the locale is UTF-8, hence `LANG=C.UTF-8`.
- `shell: bash` is used for the test step so the same command works on both runners.

## Verification Notes

The workflow run on the pull request is the verification.
