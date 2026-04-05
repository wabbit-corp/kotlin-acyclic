package one.wabbit.acyclic.idea

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.facet.KotlinFacet

internal const val EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY =
    "kotlin.k2.only.bundled.compiler.plugins.enabled"
internal const val ACYCLIC_COMPILER_PLUGIN_MARKER = "kotlin-acyclic-plugin"
internal const val ACYCLIC_GRADLE_PLUGIN_ID = "one.wabbit.acyclic"
private const val ACYCLIC_GRADLE_PLUGIN_ARTIFACT_MARKER = "kotlin-acyclic-gradle-plugin"
private const val MAX_GRADLE_BUILD_SCAN_DEPTH = 6

internal data class AcyclicCompilerPluginMatch(
    val ownerName: String,
    val classpaths: List<String>,
)

internal data class AcyclicCompilerPluginScan(
    val projectLevelMatch: AcyclicCompilerPluginMatch?,
    val moduleMatches: List<AcyclicCompilerPluginMatch>,
    val gradleBuildFiles: List<String>,
) {
    val hasMatches: Boolean
        get() = projectLevelMatch != null || moduleMatches.isNotEmpty() || gradleBuildFiles.isNotEmpty()
}

internal object AcyclicCompilerPluginDetector {
    fun scan(project: Project): AcyclicCompilerPluginScan {
        val gradleBuildFiles =
            project.basePath?.let { basePath ->
                matchingGradleBuildFiles(Path.of(basePath))
            }.orEmpty()
        val projectClasspaths =
            matchingClasspaths(
                KotlinCommonCompilerArgumentsHolder.getInstance(project)
                    .settings
                    .pluginClasspaths
                    .orEmpty()
                    .asList(),
            )
        val projectMatch =
            projectClasspaths.takeIf { it.isNotEmpty() }?.let { classpaths ->
                AcyclicCompilerPluginMatch(
                    ownerName = project.name,
                    classpaths = classpaths,
                )
            }
        val moduleMatches =
            ModuleManager.getInstance(project).modules.mapNotNull { module ->
                scanModule(module)
            }
        return AcyclicCompilerPluginScan(
            projectLevelMatch = projectMatch,
            moduleMatches = moduleMatches,
            gradleBuildFiles = gradleBuildFiles,
        )
    }

    fun matchingClasspaths(classpaths: Iterable<String>): List<String> =
        classpaths
            .filter(::isAcyclicCompilerPluginPath)
            .distinct()

    fun isAcyclicCompilerPluginPath(classpath: String): Boolean {
        val normalized = classpath.replace('\\', '/').lowercase()
        return normalized.contains(ACYCLIC_COMPILER_PLUGIN_MARKER)
    }

    fun matchingGradleBuildFiles(projectRoot: Path): List<String> {
        if (!Files.isDirectory(projectRoot)) {
            return emptyList()
        }
        Files.walk(projectRoot, MAX_GRADLE_BUILD_SCAN_DEPTH).use { paths ->
            return paths
                .filter(Files::isRegularFile)
                .map { path -> projectRoot.relativize(path).normalize() }
                .filter(::isGradleBuildFileCandidate)
                .filter { relativePath ->
                    runCatching {
                        isAcyclicGradlePluginReference(
                            Files.readString(projectRoot.resolve(relativePath)),
                        )
                    }.getOrDefault(false)
                }.map { relativePath ->
                    relativePath.toString().replace('\\', '/')
                }.distinct()
                .sorted()
                .collect(Collectors.toList())
        }
    }

    fun isAcyclicGradlePluginReference(content: String): Boolean {
        val normalized = content.lowercase()
        return normalized.contains(ACYCLIC_GRADLE_PLUGIN_ID) ||
            normalized.contains(ACYCLIC_GRADLE_PLUGIN_ARTIFACT_MARKER)
    }

    private fun scanModule(module: Module): AcyclicCompilerPluginMatch? {
        val facet = KotlinFacet.get(module) ?: return null
        val classpaths =
            matchingClasspaths(
                facet.configuration.settings
                    .mergedCompilerArguments
                    ?.pluginClasspaths
                    .orEmpty()
                    .asList(),
            )
        if (classpaths.isEmpty()) {
            return null
        }
        return AcyclicCompilerPluginMatch(
            ownerName = module.name,
            classpaths = classpaths,
        )
    }

    private fun isGradleBuildFileCandidate(path: Path): Boolean {
        if (path.any { segment ->
                segment.toString() in setOf(".git", ".gradle", ".idea", "build", "out")
            }
        ) {
            return false
        }
        val fileName = path.fileName?.toString() ?: return false
        return fileName.endsWith(".gradle") ||
            fileName.endsWith(".gradle.kts") ||
            fileName.endsWith(".versions.toml")
    }
}
