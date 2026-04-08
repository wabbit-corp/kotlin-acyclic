// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

private const val ACYCLIC_COMPILER_PLUGIN_ID = "one.wabbit.acyclic"
private const val ACYCLIC_COMPILER_PLUGIN_GROUP = "one.wabbit"
private const val ACYCLIC_COMPILER_PLUGIN_ARTIFACT = "kotlin-acyclic-plugin"
private const val ACYCLIC_EXTENSION_NAME = "acyclic"
// ACYCLIC_GRADLE_PLUGIN_VERSION is generated into the main source set by build.gradle.kts.

/**
 * Gradle bridge for the `one.wabbit.acyclic` Kotlin compiler plugin.
 *
 * The plugin registers the [AcyclicGradleExtension], makes the compiler plugin applicable to every
 * Kotlin compilation in the target project, and forwards the typed Gradle settings as compiler
 * plugin options.
 *
 * It also resolves the Kotlin-line-specific compiler plugin artifact automatically, so consumers
 * can depend on the plain Gradle plugin version while the implementation artifact still tracks the
 * Kotlin compiler line in its own version suffix.
 *
 * Most builds should use this plugin together with the annotations library
 * `one.wabbit:kotlin-acyclic`.
 */
class AcyclicGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create(ACYCLIC_EXTENSION_NAME, AcyclicGradleExtension::class.java)
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(AcyclicGradleExtension::class.java)
        return project.provider {
            listOf(
                SubpluginOption("compilationUnits", extension.compilationUnits.get().cliValue),
                SubpluginOption("declarations", extension.declarations.get().cliValue),
                SubpluginOption("declarationOrder", extension.declarationOrder.get().cliValue),
            )
        }
    }

    override fun getCompilerPluginId(): String = ACYCLIC_COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = ACYCLIC_COMPILER_PLUGIN_GROUP,
            artifactId = ACYCLIC_COMPILER_PLUGIN_ARTIFACT,
            version =
                compilerPluginArtifactVersion(
                    baseVersion = ACYCLIC_GRADLE_PLUGIN_VERSION,
                    kotlinVersion = currentKotlinGradlePluginVersion(),
                ),
        )
}
