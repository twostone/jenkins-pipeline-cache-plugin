# Agent Instructions

Jenkins plugin providing the `cache`/`hashFiles` pipeline steps. S3-backed (MinIO first-class), single Maven module, package `io.jenkins.plugins.pipeline.cache`.

## Build & Test

- JDK 21 required — the enforcer plugin hard-fails on any other version (`[21,22)`).
- Full build/test (mirrors CI): `mvn -B verify`
- Run one test: `mvn -B test -Dtest=CacheStepTest`
- `CacheStepTest`/`CacheCleanupTaskTest` need Docker: they spin up MinIO + MinIO-mc via Testcontainers plus a `JenkinsRule` with a slave executor. `ConfigurationTest` instead drives the real config UI via `JenkinsSessionRule` + HtmlUnit (no Docker).
- Benchmarks are excluded from normal runs and only run via the profile: `mvn -B verify -Pbenchmarks` (only `**/*Benchmark.java`, prints `[BENCHMARK]` lines grepped by the PR benchmark workflow — don't change that line format).
- Tests are JUnit 4 + `JenkinsRule`; follow the existing `@ClassRule` pattern (container + mc + Jenkins rule, random UUID bucket per test).

## Layout

- `CacheStep.java` / `HashFilesStep.java` — the two pipeline steps (entrypoint of the plugin).
- `agent/` — master-to-agent callables doing actual tar/archive + S3 work on the agent node.
- `s3/` — S3 access layer (`CacheItemRepository`, `S3OutputStream`); objects carry `CREATED`/`LAST_ACCESS` metadata in unix-ms.
- `.github/workflows/ci.yml` (push/PR → `mvn verify`), `benchmark.yml` (PR → base-vs-head comparison), `release.yml` (manual).

## Gotchas

- Caches are never overwritten: restoring/upsert skips if the key exists; a failed inner step does not save the cache (documented behavior, not a bug).
- `includes`/`excludes` are Ant patterns relative to `path`; `hashFiles` patterns are relative to the workspace — easy to mix up.
- Releases go through the manual GitHub workflow (`release:prepare`/`perform` + tag); don't bump versions or tag locally.
