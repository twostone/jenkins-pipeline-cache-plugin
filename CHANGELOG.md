# Changelog

## [1.1.2](https://github.com/twostone/jenkins-pipeline-cache-plugin/compare/v1.1.1...v1.1.2) (2026-10-09)


### Bug Fixes

* **backup:** abort upload instead of saving partial cache on failure ([d5b8ee4](https://github.com/twostone/jenkins-pipeline-cache-plugin/commit/d5b8ee485bb7616beef49cd2cd2bd650ab1d25a2))
* **restore:** update last access with multipart copy in the background ([eaadf6a](https://github.com/twostone/jenkins-pipeline-cache-plugin/commit/eaadf6ab5051e3f2a5f2b528403a669871afea48))

## [1.1.1](https://github.com/twostone/jenkins-pipeline-cache-plugin/compare/v1.1.0...v1.1.1) (2026-10-08)


### Bug Fixes

* configure async HTTP client explicitly ([195aaea](https://github.com/twostone/jenkins-pipeline-cache-plugin/commit/195aaea6765a5dd6322f5463c38c3e1beb146340))
* **release:** gate publish job on releases_created and guard outputs ([294877e](https://github.com/twostone/jenkins-pipeline-cache-plugin/commit/294877eaaa806f654deda95c8d5928298b9c1a66))

## [1.1.0](https://github.com/twostone/jenkins-pipeline-cache-plugin/compare/v1.0.1-SNAPSHOT...v1.1.0) (2026-10-08)


### Features

* **release:** automate releases via Release-Please and add Renovate ([d938729](https://github.com/twostone/jenkins-pipeline-cache-plugin/commit/d938729959518d13dec2a893bae464e54d0198e0))
* support credentials from the AWS Credentials plugin ([2727962](https://github.com/twostone/jenkins-pipeline-cache-plugin/commit/2727962e8752dd4e475341453a897f73248c3b19))


### Bug Fixes

* bump aws-java-sdk2 plugins to 2.42.33-70 ([0b56ac2](https://github.com/twostone/jenkins-pipeline-cache-plugin/commit/0b56ac27f40310348252dd105182ccd9f5c96c41))
