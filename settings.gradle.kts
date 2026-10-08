/*
 * Copyright (c) 2024-2025 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent { snapshotsOnly() }
        }
        // GRNET fork: GRNET's releases of the OpenID4VCI library (versions *-grnet.N) come
        // from its own Maven repository, and only from there. See .github/README.md.
        exclusiveContent {
            forRepository {
                maven {
                    name = "grnetOpenId4Vci"
                    url = uri(
                        providers.gradleProperty("grnetOpenId4VciMavenUrl")
                            .getOrElse("https://grnet.github.io/eudi-lib-jvm-openid4vci-kt/maven/")
                    )
                }
            }
            filter {
                includeVersionByRegex("eu\\.europa\\.ec\\.eudi", "eudi-lib-jvm-openid4vci-kt", ".*-grnet\\..*")
            }
        }
    }
}

rootProject.name = "eudi-lib-android-wallet-core"
include(":wallet-core")
include(":document-manager")
include(":transfer-manager")
