# Agent Instructions

Jenkins plugin providing the `cache`/`hashFiles` pipeline steps. S3-backed (MinIO first-class), single Maven module, package `io.jenkins.plugins.pipeline.cache`.

## Build & Test

- JDK 21 required — the enforcer plugin hard-fails on any other version (`[21,22)`).
- Full build/test (mirrors CI): `mvn -B verify`
- Run one test: `mvn -B test -Dtest=CacheStepTest`
- `CacheStepTest`/`CacheCleanupTaskTest` need Docker: they spin up MinIO + MinIO-mc via Testcontainers plus a `JenkinsRule` with a slave executor. `ConfigurationTest` instead drives the real config UI via `JenkinsSessionRule` + HtmlUnit (no Docker).
- Benchmarks are excluded from normal runs and only run via the profile: `mvn -B verify -Pbenchmarks` (only `**/*Benchmark.java`, prints `[BENCHMARK]` lines grepped by the benchmark workflow — don't change that line format).
- Tests are JUnit 4 + `JenkinsRule`; follow the existing `@ClassRule` pattern (container + mc + Jenkins rule, random UUID bucket per test).

## Layout

- `CacheStep.java` / `HashFilesStep.java` — the two pipeline steps (entrypoint of the plugin).
- `agent/` — master-to-agent callables doing actual tar/archive + S3 work on the agent node.
- `s3/` — S3 access layer (`CacheItemRepository`, `S3OutputStream`); objects carry `CREATED`/`LAST_ACCESS` metadata in unix-ms.
- `.github/workflows/ci.yml` (push/PR → `mvn verify`), `benchmark.yml` (manual → base-vs-head comparison in the run summary), `release.yml` (manual).

## Gotchas

- Caches are never overwritten: restoring/upsert skips if the key exists; a failed inner step does not save the cache (documented behavior, not a bug).
- `includes`/`excludes` are Ant patterns relative to `path`; `hashFiles` patterns are relative to the workspace — easy to mix up.
- Releases are fully automated: Release-Please (`release-please-config.json`) opens a release PR on `main` after conventional commits land; merging it tags (`vX.Y.Z`), creates the GitHub Release, and opens a follow-up PR bumping the pom to the next `-SNAPSHOT`. The `publish` job in `release-please.yml` (same run, after the release is created) then verifies, deploys to GitHub Packages, and uploads the `.hpi`. Force a version with a `Release-As: X.Y.Z` commit body. Don't bump versions or tag locally.
