[![CI](https://github.com/TwoStone/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/TwoStone/jenkins-pipeline-cache-plugin/actions/workflows/ci.yml)

A cloud native file cache for Jenkins pipelines. The files are stored in a S3-Bucket. The functionality is very similar to the one provided by [GitHub Actions](https://docs.github.com/en/actions/advanced-guides/caching-dependencies-to-speed-up-workflows).

# Motivation
The primary goal is to have a file cache for so called `hot agent nodes`. Those nodes are started on demand when an execution is scheduled by Jenkins and killed after the execution is finished (e.g. by using the [kubernetes-plugin](https://github.com/jenkinsci/kubernetes-plugin) or [nomad-plugin](https://github.com/jenkinsci/nomad-plugin)). This is fine but has also some drawbacks and some of them can be solved by having a file cache in place (e.g. to cache build dependencies or statistic data for code analysis or whatever data you want to be present for the next build execution).

# Installation
* Download the latest version (see [releases](https://github.com/TwoStone/jenkins-pipeline-cache-plugin/releases))
* Complete the installation via `Manage Jenkins -> Manage Plugins -> Advanced -> Upload Plugin`

For automated installations via `plugin.txt` you can use an entry like below:
```
jenkins-pipeline-cache::https://github.com/TwoStone/jenkins-pipeline-cache-plugin/releases/download/v1.1.0/jenkins-pipeline-cache-1.1.0.hpi
```

# Configuration
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Configure your credentials (see [Credentials](#credentials) below)
* Set `Bucket`
* Set `Region`
* Optionally set `Endpoint` if you use an S3-compatible service other than AWS S3
* Click `Test connection`

## Credentials
The plugin supports three ways to authenticate to the S3 bucket, checked in this order:

1. **Stored Jenkins credential** - select an existing *username with password* credential in the `Credentials` dropdown. The credential's username is used as the S3 access key ID and its password as the secret access key. This is the recommended option, as the credentials are managed centrally in Jenkins. Credentials from the optional [AWS Credentials plugin](https://github.com/jenkinsci/aws-credentials-plugin) are listed in the same dropdown and supported as well — the plugin uses the credential's own resolution (including STS session tokens) in that case.
2. **Explicit credentials** - set `Username` (aka S3-Access-Key) and `Password` (aka S3-Secret-Key) directly in the configuration.
3. **Default AWS credential provider chain** - if you configure no credential and no explicit username/password, the plugin resolves credentials from the [default AWS credential provider chain](https://docs.aws.amazon.com/sdkref/latest/guide/standardized-credentials.html) on the controller (e.g. instance role, environment variables, profile files). This is useful when the Jenkins controller runs on a host with an IAM instance profile or equivalent.

If several options are configured, the stored credential wins, followed by the explicit credentials.

The plugin requires the following permissions in S3 for the bucket:
* s3:HeadObject
* s3:GetObject
* s3:ListBucket
* s3:PutObject
* s3:DeleteObject - Only if the CleanupTask is activated (threshold > 0)

# Usage
Below you can find an example where the local maven repository of the [spring-petclinic](https://github.com/spring-projects/spring-petclinic) project is cached.
```
node {
    git(url: 'https://github.com/spring-projects/spring-petclinic', branch: 'main')
    cache(path: "$HOME/.m2/repository", key: "petclinic-${hashFiles('**/pom.xml')}") {
        sh './mvnw package'
    }
}
```
The `path` parameter points to the local maven repository and the `key` parameter is the hash sum of all maven poms, prefixed by a dash and the project name.

The `hashFiles` method is optional but can be helpful to generate more precise keys. The idea is to collect all files which have impact to the cache and then create a hash sum from them (e.g. `hashFiles('**/pom.xml')` creates one hash sum over all maven poms in the workspace).

If the job gets executed, the plugin tries to restore the maven repository from the cache by using the given `key`. Then the inner-step gets executed and if this was successful and the cache doesn't exist yet then the `path` gets cached.

Below you can find a complete list of the `cache` step parameters:

| Name        | Required | Description                                                                                                                                                                                                                                         | Default                     | Example                                                                                                                          |
|-------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| path        | x        | Path to the directory which we want to be cached (absolute or relative to the workspace)                                                                                                                                                            |                             | `$HOME/.m2/repository` - cache the local maven repository                                                                        |
| key         | x        | Identifier which is assigned to the cache.                                                                                                                                                                                                          |                             | `maven-4f98f59e877ecb84ff75ef0fab45bac5`                                                                                         |
| restoreKeys |          | Additional keys which are used when the cache gets restored. The plugin tries to resolve them in the defined order (`key` first then the `restoreKeys`) and in case this was not successful then the latest key with the same prefix gets restored. |                             | `['maven-', 'petclinic-']` - restore the latest cache where the key starts with `maven-` or `petclinic-` if the `key` not exists |
| includes    |          | Ant-style pattern applied to the `path` to filter the files which are included.                                                                                                                                                                     | `**/*` - includes all files | `**/*.xml` or `**/*.xml,**/*.html` see [here](https://ant.apache.org/manual/dirtasks.html) for more details                      |
| excludes    |          | Ant-style pattern applied to the `path` to filter the files which are excluded.                                                                                                                                                                     | Excludes no files           | see `includes`                                                                                                                   |

# Storage providers
Any S3 compatible storage provider should work. MinIO is supported first class, because all the integration tests are executed against MinIO.

In order to use an alternative provider, you probably have to change the `Endpoint` parameter.
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Update the `Endpoint` parameter
* Click `Test connection`

# Cleanup
You can define a threshold in megabyte if you want to limit the total cache size. If the value is > 0 then the plugin checks every hour the threshold and removes last recently used items from the cache as long as the total cache size is smaller than the threshold again (LRU).
* Go to `Manage Jenkins -> Configure System -> Cache Plugin`
* Update the `Threshold` parameter

# Disclaimer
Anyone which can create/execute build jobs has basically also access to all caches. The 'attacker' just needs a way to execute the plugin, and they need to know the key which is assigned to a particular cache. There is no list available where all the keys are listed but the build logs contain them. The plugin guarantees that the same key is not created twice and also that an existing key is not replaced, but it not guarantees that a restored cache was not manipulated by someone else which has access to the S3 bucket for example.

As a general advice, sensitive data or data which cannot be restored from somewhere else or not regenerated should not be stored in caches. It should also not a big deal, besides that the build takes longer, if a cache has been deleted (e.g. by accident, by the cleanup task, by a data crash or ...).

# Pitfalls
* the `hashFiles` step expects an [Ant-Style](https://ant.apache.org/manual/dirtasks.html) pattern relative to the workspace as parameter
* the `includes/excludes` parameter must be an [Ant-Style](https://ant.apache.org/manual/dirtasks.html) pattern relative to the `path`
* the cache gets not stored if the `key` already exists or the inner-step has been failed (e.g. unit-test failures)
* existing files are replaced but not removed when the cache gets restored
* the plugin creates a tar archive from the path and stores it as an S3 object
* the S3 object contains metadata
  * CREATED - Unix time is ms when the cache was created
  * LAST_ACCESS - Unix time is ms when the cache was accessed last

# Development

## CI
Every push and pull request triggers the [CI workflow](.github/workflows/ci.yml) which runs `mvn verify` and uploads the `.hpi` artifact.

Dependency updates are managed automatically by [Renovate](.github/renovate.json): it opens pull requests with updated versions (e.g. the AWS SDK for Java, Testcontainers), which are validated like any other pull request. Version upgrades of the Jenkins core, the Jenkins parent pom, and the plugin [BOM](https://www.jenkins.io/doc/developer/plugin-development/dependency-security/bom/) (which move together when the LTS line changes, e.g. `bom-2.504.x`) are intentionally excluded from automation and applied manually.

## Benchmarks
The [benchmark workflow](.github/workflows/benchmark.yml) is run manually (Actions > Benchmark Comparison > Run workflow). It executes the benchmark suite on the selected branch and on a base ref (default `main`), then writes a comparison table with throughput and latency deltas to the run summary.

To run benchmarks locally:
```bash
mvn -B verify -Pbenchmarks
```

## Releasing
Releases are automated via [Release-Please](https://github.com/googleapis/release-please), configured in [release-please-config.json](../release-please-config.json):

1. As soon as the next commit after the last release carries a conventional commit type (`fix:`, `feat:`, `docs:`, ...), Release-Please opens a release pull request on `main` (labeled `autorelease: pending`) which keeps the `pom.xml` version bump and `CHANGELOG.md` in sync as more commits land.
2. Merging that pull request cuts the release: Release-Please tags the commit (`vX.Y.Z`), creates the GitHub Release with generated notes, and opens a follow-up pull request that bumps the pom to the next `-SNAPSHOT` version.
3. The [release workflow](.github/workflows/release-please.yml) then runs the full test suite on the tagged commit, deploys the artifacts to GitHub Packages (Maven coordinates `com.github.twostone:jenkins-pipeline-cache:X.Y.Z`), and uploads the `.hpi` to the GitHub Release.

To force a specific version, merge any commit on `main` whose body contains `Release-As: X.Y.Z`.

Old releases use the `jenkins-pipeline-cache-X.Y.Z` tag format; their download links remain valid.

# Further reading
* [CacheStep.java](./src/main/java/io/jenkins/plugins/pipeline/cache/CacheStep.java) - implements the `cache` pipeline step
* [CacheStepTest](./src/test/java/io/jenkins/plugins/pipeline/cache/CacheStepTest.java) - checks that the cache pipeline step works as expected
* [HashFilesStep](./src/main/java/io/jenkins/plugins/pipeline/cache/HashFilesStep.java) - implements the `hashFiles` pipeline step
