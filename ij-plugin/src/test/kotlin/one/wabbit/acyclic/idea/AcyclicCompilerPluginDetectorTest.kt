package one.wabbit.acyclic.idea

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AcyclicCompilerPluginDetectorTest {
    @Test
    fun detectsAcyclicCompilerPluginInMavenLocalPath() {
        assertTrue(
            AcyclicCompilerPluginDetector.isAcyclicCompilerPluginPath(
                "/Users/example/.m2/repository/one/wabbit/kotlin-acyclic-plugin/0.0.1/kotlin-acyclic-plugin-0.0.1.jar",
            ),
        )
    }

    @Test
    fun detectsAcyclicCompilerPluginInBuildLibsPath() {
        assertTrue(
            AcyclicCompilerPluginDetector.isAcyclicCompilerPluginPath(
                "/workspace/kotlin-acyclic-plugin/build/libs/kotlin-acyclic-plugin-0.0.1.jar",
            ),
        )
    }

    @Test
    fun ignoresUnrelatedCompilerPluginPaths() {
        assertFalse(
            AcyclicCompilerPluginDetector.isAcyclicCompilerPluginPath(
                "/workspace/kotlin-typeclasses-plugin/build/libs/kotlin-typeclasses-plugin-0.0.1.jar",
            ),
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
                ),
            )

        assertEquals(
            listOf("/workspace/kotlin-acyclic-plugin/build/libs/kotlin-acyclic-plugin-0.0.1.jar"),
            matches,
        )
    }

    @Test
    fun detectsAcyclicGradlePluginIdInBuildScript() {
        assertTrue(
            AcyclicCompilerPluginDetector.isAcyclicGradlePluginReference(
                """
                plugins {
                    id("one.wabbit.acyclic")
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun detectsAcyclicGradlePluginIdInVersionCatalog() {
        assertTrue(
            AcyclicCompilerPluginDetector.isAcyclicGradlePluginReference(
                """
                [plugins]
                acyclic = { id = "one.wabbit.acyclic", version = "0.0.1" }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun matchingGradleBuildFilesFindsPluginReferencesAndIgnoresBuildOutputs() {
        val projectRoot = Files.createTempDirectory("acyclic-idea-detector-test")
        projectRoot.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("one.wabbit.acyclic")
            }
            """.trimIndent(),
        )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot.resolve("gradle/libs.versions.toml").writeText(
            """
            [plugins]
            acyclic = { id = "one.wabbit.acyclic", version = "0.0.1" }
            """.trimIndent(),
        )
        projectRoot.resolve("build/generated").createDirectories()
        projectRoot.resolve("build/generated/build.gradle.kts").writeText(
            """
            plugins {
                id("one.wabbit.acyclic")
            }
            """.trimIndent(),
        )

        val matches = AcyclicCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(
            listOf("build.gradle.kts", "gradle/libs.versions.toml"),
            matches,
        )
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
                                ),
                            ),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                    ),
                registryUpdated = true,
            )

        assertTrue(message.contains("project settings"))
        assertTrue(message.contains("module app"))
        assertTrue(message.contains("Gradle build files"))
        assertTrue(message.contains("all non-bundled K2 compiler plugins"))
    }
}
