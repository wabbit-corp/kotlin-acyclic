// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.acyclic.idea

import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.lang.reflect.Proxy
import java.util.MissingResourceException
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import one.wabbit.ijplugin.common.CompilerPluginMatch as AcyclicCompilerPluginMatch
import one.wabbit.ijplugin.common.CompilerPluginScan as AcyclicCompilerPluginScan
import one.wabbit.ijplugin.common.EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY
import one.wabbit.ijplugin.common.ExternalCompilerPluginRegistryState as AcyclicExternalCompilerPluginRegistryState
import one.wabbit.ijplugin.common.ExternalCompilerPluginRegistrySupport
import one.wabbit.ijplugin.common.ExternalCompilerPluginSessionRegistrationResult
import one.wabbit.ijplugin.common.GradleBuildFileMatch as AcyclicGradleBuildFileMatch
import one.wabbit.ijplugin.common.IdeSupportActivationState as AcyclicIdeSupportActivationState
import one.wabbit.ijplugin.common.IdeSupportProjectStateTracker

class AcyclicCompilerPluginDetectorTest {
    @Test
    fun detectsAcyclicCompilerPluginInMavenLocalPath() {
        assertTrue(
            AcyclicCompilerPluginDetector.isCompilerPluginPath(
                "/Users/example/.m2/repository/one/wabbit/kotlin-acyclic-plugin/0.0.1/kotlin-acyclic-plugin-0.0.1.jar"
            )
        )
    }

    @Test
    fun detectsAcyclicCompilerPluginInBuildLibsPath() {
        assertTrue(
            AcyclicCompilerPluginDetector.isCompilerPluginPath(
                "/workspace/kotlin-acyclic-plugin/build/libs/kotlin-acyclic-plugin-0.0.1.jar"
            )
        )
    }

    @Test
    fun ignoresUnrelatedCompilerPluginPaths() {
        assertFalse(
            AcyclicCompilerPluginDetector.isCompilerPluginPath(
                "/workspace/kotlin-typeclasses-plugin/build/libs/kotlin-typeclasses-plugin-0.0.1.jar"
            )
        )
    }

    @Test
    fun matchingClasspathsDeduplicatesMatches() {
        val matches =
            AcyclicCompilerPluginDetector.matchingClasspaths(
                listOf(
                    "/workspace/kotlin-acyclic-plugin/build/libs/kotlin-acyclic-plugin-0.0.1.jar",
                    "/workspace/kotlin-acyclic-plugin/build/libs/kotlin-acyclic-plugin-0.0.1.jar",
                    "/workspace/other-plugin/build/libs/other-plugin-0.0.1.jar",
                )
            )

        assertEquals(
            listOf("/workspace/kotlin-acyclic-plugin/build/libs/kotlin-acyclic-plugin-0.0.1.jar"),
            matches,
        )
    }

    @Test
    fun `build file only scan requires Gradle import`() {
        val scan =
            AcyclicCompilerPluginScan(
                projectLevelMatch = null,
                moduleMatches = emptyList(),
                gradleBuildFiles = listOf("build.gradle.kts"),
            )

        assertFalse(scan.hasImportedCompilerPluginMatches)
        assertTrue(scan.hasMatches)
        assertTrue(scan.requiresGradleImport)
    }

    @Test
    fun `imported classpath scan does not require Gradle import`() {
        val scan =
            AcyclicCompilerPluginScan(
                projectLevelMatch =
                    AcyclicCompilerPluginMatch(
                        ownerName = "demo",
                        classpaths = listOf("/tmp/kotlin-acyclic-plugin.jar"),
                    ),
                moduleMatches = emptyList(),
                gradleBuildFiles = listOf("build.gradle.kts"),
            )

        assertTrue(scan.hasImportedCompilerPluginMatches)
        assertTrue(scan.hasMatches)
        assertFalse(scan.requiresGradleImport)
    }

    @Test
    fun detectsAcyclicGradlePluginIdInBuildScript() {
        assertTrue(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                plugins {
                    id("one.wabbit.acyclic")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `detects Acyclic Gradle plugin id in single-line plugins block`() {
        assertTrue(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """plugins { id("one.wabbit.acyclic") }"""
            )
        )
    }

    @Test
    fun `detects Acyclic pluginManager apply`() {
        assertTrue(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """pluginManager.apply("one.wabbit.acyclic")"""
            )
        )
    }

    @Test
    fun `version catalog plugin id does not count as a direct Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                [plugins]
                acyclic = { id = "one.wabbit.acyclic", version = "0.0.1" }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `commented version catalog plugin id does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                # acyclic = { id = "one.wabbit.acyclic", version = "0.0.1" }
                [plugins]
                kotlin = { id = "org.jetbrains.kotlin.jvm", version = "2.3.10" }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `commented version catalog artifact coordinate does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                [libraries]
                # acyclic-gradle = { module = "one.wabbit:kotlin-acyclic-gradle-plugin", version = "0.0.1" }
                kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version = "2.3.10" }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `detects Groovy plugins block form`() {
        assertTrue(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                plugins {
                    id 'one.wabbit.acyclic'
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `detects Groovy apply plugin form`() {
        assertTrue(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                apply plugin: 'one.wabbit.acyclic'
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `unused Groovy classpath dependency form does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                buildscript {
                    dependencies {
                        classpath 'one.wabbit:kotlin-acyclic-gradle-plugin:0.0.1'
                    }
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `unused Groovy map notation dependency form does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                buildscript {
                    dependencies {
                        classpath group: 'one.wabbit', name: 'kotlin-acyclic-gradle-plugin', version: '0.0.1'
                    }
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `multiline string containing plugin syntax does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                val docs = ${"\"\"\""}
                    id("one.wabbit.acyclic")
                ${"\"\"\""}
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `plugin id with apply false does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                plugins {
                    id("one.wabbit.acyclic") apply false
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `apply false does not hide later same-line application`() {
        assertTrue(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """plugins { id("one.wabbit.acyclic") apply false; id("one.wabbit.acyclic") }"""
            )
        )
    }

    @Test
    fun `commented plugin id does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                // id("one.wabbit.acyclic")
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `string literal containing plugin syntax does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                val doc = "plugins { id(\"one.wabbit.acyclic\") }"
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `string literal containing artifact syntax does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                val doc = "classpath 'one.wabbit:kotlin-acyclic-gradle-plugin:0.0.1'"
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun `string literal mentioning plugin id does not count as a Gradle reference`() {
        assertFalse(
            AcyclicCompilerPluginDetector.isDirectGradlePluginReference(
                """
                val note = "remember one.wabbit.acyclic later"
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun matchingGradleBuildFilesFindsPluginReferencesAndSkipsExcludedDirectorySubtrees() {
        val projectRoot = Files.createTempDirectory("acyclic-idea-detector-test")
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("one.wabbit.acyclic")
                }
                """
                    .trimIndent()
            )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot
            .resolve("gradle/libs.versions.toml")
            .writeText(
                """
                [plugins]
                acyclic = { id = "one.wabbit.acyclic", version = "0.0.1" }

                [libraries]
                # acyclic-gradle = { module = "one.wabbit:kotlin-acyclic-gradle-plugin", version = "0.0.1" }
                """
                    .trimIndent()
            )
        listOf(".git", ".gradle", ".idea", "build", "out").forEach { excludedDir ->
            projectRoot.resolve("$excludedDir/generated").createDirectories()
            projectRoot
                .resolve("$excludedDir/generated/build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        id("one.wabbit.acyclic")
                    }
                    """
                        .trimIndent()
                )
        }

        val matches = AcyclicCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(listOf("build.gradle.kts"), matches)
    }

    @Test
    fun `matchingGradleBuildFiles uses version catalog aliases only when applied in build script`() {
        val projectRoot = Files.createTempDirectory("acyclic-idea-detector-alias-test")
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins { alias(libs.plugins.acyclic) }
                """
                    .trimIndent()
            )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot
            .resolve("gradle/libs.versions.toml")
            .writeText(
                """
                [plugins]
                acyclic = { id = "one.wabbit.acyclic", version = "0.0.1" }
                """
                    .trimIndent()
            )

        val matches = AcyclicCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(listOf("build.gradle.kts"), matches)
    }

    @Test
    fun `matchingGradleBuildFileMatches retain originating root path`() {
        val projectRoot = Files.createTempDirectory("acyclic-idea-detector-root-path-test")
        val appRoot = projectRoot.resolve("app")
        appRoot.createDirectories()
        appRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("one.wabbit.acyclic")
                }
                """
                    .trimIndent()
            )

        val matches = AcyclicCompilerPluginDetector.matchingGradleBuildFileMatches(listOf(appRoot))

        assertEquals(
            listOf(
                AcyclicGradleBuildFileMatch(
                    relativePath = "build.gradle.kts",
                    rootPath = appRoot.toAbsolutePath().normalize().toString().replace('\\', '/'),
                )
            ),
            matches,
        )
    }

    @Test
    fun `matchingGradleBuildFiles does not leak version catalog aliases across selected roots`() {
        val projectRoot = Files.createTempDirectory("acyclic-idea-detector-root-set-alias-test")
        val appRoot = projectRoot.resolve("app")
        appRoot.createDirectories()
        appRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    alias(libs.plugins.kotlin.acyclic)
                }
                """
                    .trimIndent()
            )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot
            .resolve("gradle/libs.versions.toml")
            .writeText(
                """
                [plugins]
                kotlin-acyclic = { id = "one.wabbit.acyclic", version = "0.0.1" }
                """
                    .trimIndent()
            )

        val matches =
            AcyclicCompilerPluginDetector.matchingGradleBuildFiles(listOf(projectRoot, appRoot))

        assertEquals(emptyList(), matches)
    }

    @Test
    fun `matchingGradleBuildFiles ignores version catalog aliases that are not applied`() {
        val projectRoot = Files.createTempDirectory("acyclic-idea-detector-catalog-only-test")
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot
            .resolve("gradle/libs.versions.toml")
            .writeText(
                """
                [plugins]
                acyclic = { id = "one.wabbit.acyclic", version = "0.0.1" }
                """
                    .trimIndent()
            )

        val matches = AcyclicCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(emptyList(), matches)
    }

    @Test
    fun `matchingGradleBuildFiles ignores nested fixture build files`() {
        val projectRoot = Files.createTempDirectory("acyclic-idea-detector-fixture-test")
        projectRoot.resolve("docs/examples").createDirectories()
        projectRoot
            .resolve("docs/examples/build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("one.wabbit.acyclic")
                }
                """
                    .trimIndent()
            )

        val matches = AcyclicCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(emptyList(), matches)
    }

    @Test
    fun `matchingGradleBuildFiles ignores settings scripts that only mention plugin id`() {
        val projectRoot = Files.createTempDirectory("acyclic-idea-detector-settings-test")
        projectRoot
            .resolve("settings.gradle.kts")
            .writeText(
                """
                pluginManagement {
                    plugins {
                        id("one.wabbit.acyclic") version "0.0.1"
                    }
                }
                """
                    .trimIndent()
            )
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )

        val matches = AcyclicCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(emptyList(), matches)
    }

    @Test
    fun `matchingGradleBuildFiles does not reject projects rooted under build-like path segments`() {
        val sandboxRoot = Files.createTempDirectory("acyclic-idea-detector-root")
        val projectRoot = sandboxRoot.resolve("build/demo")
        projectRoot.createDirectories()
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("one.wabbit.acyclic")
                }
                """
                    .trimIndent()
            )

        val matches = AcyclicCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(listOf("build.gradle.kts"), matches)
    }

    @Test
    fun `missing registry keys skip registry work and continue activation`() {
        val notifications = mutableListOf<Triple<NotificationType, String, String>>()
        var registrationAttempts = 0

        val result =
            AcyclicIdeSupportCoordinator.enableIfNeeded(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch =
                            AcyclicCompilerPluginMatch(
                                ownerName = "demo",
                                classpaths = listOf("/tmp/kotlin-acyclic-plugin.jar"),
                            ),
                        moduleMatches = emptyList(),
                        gradleBuildFiles = emptyList(),
                    ),
                projectTrusted = true,
                userInitiated = false,
                registryState = AcyclicExternalCompilerPluginRegistryState.UNAVAILABLE,
                enableExternalPluginsForProjectSession = {
                    registrationAttempts += 1
                    ExternalCompilerPluginSessionRegistrationResult.FAILED
                },
                notify = { type, title, content -> notifications += Triple(type, title, content) },
            )

        assertEquals(AcyclicIdeSupportActivationState.REGISTRY_UNAVAILABLE, result.activationState)
        assertFalse(result.registryAlreadyEnabledForExternalPlugins)
        assertFalse(result.registryUpdated)
        assertEquals(0, registrationAttempts)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.INFORMATION, notifications.single().first)
        assertTrue(notifications.single().third.contains("without registry changes"))
    }

    @Test
    fun `build file only match requests Gradle import instead of reporting active support`() {
        val notifications = mutableListOf<Triple<NotificationType, String, String>>()
        var importRequests = 0
        var requestedPaths = emptyList<String>()

        val result =
            AcyclicIdeSupportCoordinator.enableIfNeeded(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch = null,
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                        gradleImportPaths = listOf("/repo/app"),
                    ),
                projectTrusted = true,
                userInitiated = true,
                registryState = AcyclicExternalCompilerPluginRegistryState.ALREADY_ALLOWED,
                enableExternalPluginsForProjectSession = {
                    ExternalCompilerPluginSessionRegistrationResult.REGISTERED_WITHOUT_CHANGE
                },
                requestGradleImport = { paths ->
                    requestedPaths = paths
                    importRequests += 1
                    true
                },
                notify = { type, title, content -> notifications += Triple(type, title, content) },
            )

        assertEquals(
            AcyclicIdeSupportActivationState.WAITING_FOR_GRADLE_IMPORT,
            result.activationState,
        )
        assertTrue(result.registryAlreadyEnabledForExternalPlugins)
        assertFalse(result.registryUpdated)
        assertTrue(result.gradleImportRequested)
        assertEquals(1, importRequests)
        assertEquals(listOf("/repo/app"), requestedPaths)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.INFORMATION, notifications.single().first)
        assertEquals(
            "Acyclic IDE support is waiting for Gradle import",
            notifications.single().second,
        )
        assertTrue(notifications.single().third.contains("Requested a Gradle import"))
        assertFalse(notifications.single().second.contains("active", ignoreCase = true))
    }

    @Test
    fun `build file only match enables registry and still waits for Gradle import without restarting analysis`() {
        val notifications = mutableListOf<Triple<NotificationType, String, String>>()
        var registryUpdated = false
        var analysisRestarts = 0

        val result =
            AcyclicIdeSupportCoordinator.enableIfNeeded(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch = null,
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                        gradleImportPaths = listOf("/repo/app"),
                    ),
                projectTrusted = true,
                userInitiated = false,
                registryState = AcyclicExternalCompilerPluginRegistryState.BLOCKING,
                enableExternalPluginsForProjectSession = {
                    registryUpdated = true
                    ExternalCompilerPluginSessionRegistrationResult.CHANGED_VALUE
                },
                requestGradleImport = { _ -> true },
                restartAnalysis = { analysisRestarts += 1 },
                notify = { type, title, content -> notifications += Triple(type, title, content) },
            )

        assertEquals(
            AcyclicIdeSupportActivationState.WAITING_FOR_GRADLE_IMPORT,
            result.activationState,
        )
        assertTrue(registryUpdated)
        assertTrue(result.registryUpdated)
        assertTrue(result.gradleImportRequested)
        assertEquals(0, analysisRestarts)
        assertEquals(1, notifications.size)
        assertEquals(
            "Acyclic IDE support is waiting for Gradle import",
            notifications.single().second,
        )
        assertTrue(
            notifications.single().third.contains("Enabled all non-bundled K2 compiler plugins")
        )
    }

    @Test
    fun `build file import request does not restart analysis when registry is already enabled`() {
        var analysisRestarts = 0

        val result =
            AcyclicIdeSupportCoordinator.enableIfNeeded(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch = null,
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                        gradleImportPaths = listOf("/repo/app"),
                    ),
                projectTrusted = true,
                userInitiated = false,
                registryState = AcyclicExternalCompilerPluginRegistryState.ALREADY_ALLOWED,
                enableExternalPluginsForProjectSession = {
                    ExternalCompilerPluginSessionRegistrationResult.REGISTERED_WITHOUT_CHANGE
                },
                requestGradleImport = { _ -> true },
                restartAnalysis = { analysisRestarts += 1 },
                notify = { _, _, _ -> },
            )

        assertTrue(result.gradleImportRequested)
        assertFalse(result.registryUpdated)
        assertEquals(0, analysisRestarts)
    }

    @Test
    fun `repeated background rescans do not request Gradle import twice`() {
        val tracker = IdeSupportProjectStateTracker()
        val sessionKey = Any()
        var importRequests = 0
        val notifications = mutableListOf<Triple<NotificationType, String, String>>()

        repeat(2) {
            AcyclicIdeSupportCoordinator.enableIfNeeded(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch = null,
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                        gradleImportPaths = listOf("/repo/app"),
                    ),
                projectTrusted = true,
                userInitiated = false,
                registryState = AcyclicExternalCompilerPluginRegistryState.ALREADY_ALLOWED,
                enableExternalPluginsForProjectSession = {
                    ExternalCompilerPluginSessionRegistrationResult.REGISTERED_WITHOUT_CHANGE
                },
                requestGradleImport = {
                    importRequests += 1
                    true
                },
                notify = { type, title, content -> notifications += Triple(type, title, content) },
                sessionKey = sessionKey,
                projectStateTracker = tracker,
            )
        }

        assertEquals(1, importRequests)
        assertEquals(1, notifications.size)
    }

    @Test
    fun `analysis restarts when imported compiler plugin matches become available after import finishes`() {
        val tracker = IdeSupportProjectStateTracker()
        val sessionKey = Any()
        var analysisRestarts = 0

        AcyclicIdeSupportCoordinator.enableIfNeeded(
            scan =
                AcyclicCompilerPluginScan(
                    projectLevelMatch = null,
                    moduleMatches = emptyList(),
                    gradleBuildFiles = listOf("build.gradle.kts"),
                    gradleImportPaths = listOf("/repo/app"),
                ),
            projectTrusted = true,
            userInitiated = false,
            registryState = AcyclicExternalCompilerPluginRegistryState.ALREADY_ALLOWED,
            enableExternalPluginsForProjectSession = {
                ExternalCompilerPluginSessionRegistrationResult.REGISTERED_WITHOUT_CHANGE
            },
            requestGradleImport = { true },
            restartAnalysis = { analysisRestarts += 1 },
            notify = { _, _, _ -> },
            sessionKey = sessionKey,
            projectStateTracker = tracker,
        )

        tracker.markImportFinished(sessionKey)

        val result =
            AcyclicIdeSupportCoordinator.enableIfNeeded(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch =
                            AcyclicCompilerPluginMatch(
                                ownerName = "demo",
                                classpaths = listOf("/tmp/kotlin-acyclic-plugin.jar"),
                            ),
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                        gradleImportPaths = listOf("/repo/app"),
                    ),
                projectTrusted = true,
                userInitiated = false,
                registryState = AcyclicExternalCompilerPluginRegistryState.ALREADY_ALLOWED,
                enableExternalPluginsForProjectSession = {
                    ExternalCompilerPluginSessionRegistrationResult.REGISTERED_WITHOUT_CHANGE
                },
                restartAnalysis = { analysisRestarts += 1 },
                notify = { _, _, _ -> },
                sessionKey = sessionKey,
                projectStateTracker = tracker,
            )

        assertFalse(result.registryUpdated)
        assertEquals(1, analysisRestarts)
    }

    @Test
    fun `coordinator prefers matched Gradle import paths over project base path fallback`() {
        val paths =
            AcyclicIdeSupportCoordinator.gradleImportPaths(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch = null,
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("app/build.gradle.kts"),
                        gradleImportPaths = listOf("/repo/app"),
                    ),
                projectBasePath = "/repo",
            )

        assertEquals(listOf("/repo/app"), paths)
    }

    @Test
    fun `user initiated refresh does not report active support when registry update fails`() {
        val notifications = mutableListOf<Triple<NotificationType, String, String>>()

        val result =
            AcyclicIdeSupportCoordinator.enableIfNeeded(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch =
                            AcyclicCompilerPluginMatch(
                                ownerName = "demo",
                                classpaths = listOf("/tmp/kotlin-acyclic-plugin.jar"),
                            ),
                        moduleMatches = emptyList(),
                        gradleBuildFiles = emptyList(),
                    ),
                projectTrusted = true,
                userInitiated = true,
                registryState = AcyclicExternalCompilerPluginRegistryState.BLOCKING,
                enableExternalPluginsForProjectSession = {
                    throw MissingResourceException(
                        "missing registry key",
                        "Registry",
                        EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY,
                    )
                },
                notify = { type, title, content -> notifications += Triple(type, title, content) },
            )

        assertFalse(result.registryAlreadyEnabledForExternalPlugins)
        assertFalse(result.registryUpdated)
        assertEquals(1, notifications.size)
        assertEquals(NotificationType.WARNING, notifications.single().first)
        assertFalse(notifications.single().second.contains("active", ignoreCase = true))
    }

    @Test
    fun `registry lookup failure reports unavailable state`() {
        var logged = false

        val registryState =
            ExternalCompilerPluginRegistrySupport.registryState(
                read = {
                    throw MissingResourceException(
                        "missing registry key",
                        "Registry",
                        EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY,
                    )
                },
                logFailure = { logged = true },
            )

        assertEquals(AcyclicExternalCompilerPluginRegistryState.UNAVAILABLE, registryState)
        assertTrue(logged)
    }

    @Test
    fun `registry update failure returns failed registration result`() {
        var logged = false

        val updated =
            ExternalCompilerPluginRegistrySupport.enableExternalPluginsForProjectSession(
                registryKey = EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY,
                project = fakeProject("demo-registry"),
                read = { true },
                update = {
                    throw MissingResourceException(
                        "missing registry key",
                        "Registry",
                        EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY,
                    )
                },
                logFailure = { logged = true },
            )

        assertEquals(ExternalCompilerPluginSessionRegistrationResult.FAILED, updated)
        assertTrue(logged)
    }

    @Test
    fun enabledMessageListsProjectAndModuleOwners() {
        val message =
            AcyclicIdeSupportCoordinator.buildEnabledMessage(
                scan =
                    AcyclicCompilerPluginScan(
                        projectLevelMatch =
                            AcyclicCompilerPluginMatch(
                                ownerName = "demo",
                                classpaths = listOf("/tmp/kotlin-acyclic-plugin.jar"),
                            ),
                        moduleMatches =
                            listOf(
                                AcyclicCompilerPluginMatch(
                                    ownerName = "app",
                                    classpaths = listOf("/tmp/kotlin-acyclic-plugin.jar"),
                                )
                            ),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                    ),
                activationState = AcyclicIdeSupportActivationState.ENABLED_NOW,
            )

        assertTrue(message.contains("project settings"))
        assertTrue(message.contains("module app"))
        assertTrue(message.contains("Gradle build files"))
        assertTrue(message.contains("all non-bundled K2 compiler plugins"))
    }

    private fun fakeProject(name: String): Project =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            when (method.name) {
                "getName" -> name
                "isDisposed" -> false
                "toString" -> "fakeProject($name)"
                else -> throw UnsupportedOperationException("Unexpected proxy call to ${method.name}")
            }
        } as Project
}
