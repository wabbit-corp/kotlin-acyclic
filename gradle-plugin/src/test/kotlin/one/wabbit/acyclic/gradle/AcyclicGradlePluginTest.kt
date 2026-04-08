// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.acyclic.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class AcyclicGradlePluginTest {
    @Test
    fun `applying the plugin registers the extension with expected defaults`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(AcyclicGradlePlugin::class.java)

        val extension = project.extensions.getByType(AcyclicGradleExtension::class.java)
        assertEquals(AcyclicEnforcementMode.OPT_IN, extension.compilationUnits.get())
        assertEquals(AcyclicEnforcementMode.DISABLED, extension.declarations.get())
        assertEquals(AcyclicDeclarationOrderMode.NONE, extension.declarationOrder.get())
    }

    @Test
    fun `plugin exposes the expected compiler plugin metadata`() {
        val plugin = AcyclicGradlePlugin()

        assertEquals("one.wabbit.acyclic", plugin.getCompilerPluginId())

        val artifact = plugin.getPluginArtifact()
        assertEquals("one.wabbit", artifact.groupId)
        assertEquals("kotlin-acyclic-plugin", artifact.artifactId)
        assertEquals(
            compilerPluginArtifactVersion(
                baseVersion = ACYCLIC_GRADLE_PLUGIN_VERSION,
                kotlinVersion = currentKotlinGradlePluginVersion(),
            ),
            artifact.version,
        )
    }

    @Test
    fun `compiler plugin artifact version appends the Kotlin version for releases`() {
        assertEquals(
            "1.2.3-kotlin-2.4.0",
            compilerPluginArtifactVersion(baseVersion = "1.2.3", kotlinVersion = "2.4.0"),
        )
    }

    @Test
    fun `compiler plugin artifact version keeps snapshot suffix at the end`() {
        assertEquals(
            "1.2.3-kotlin-2.4.0+dev-SNAPSHOT",
            compilerPluginArtifactVersion(
                baseVersion = "1.2.3+dev-SNAPSHOT",
                kotlinVersion = "2.4.0",
            ),
        )
    }

    @Test
    fun `plugin forwards extension values as compiler plugin options`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(AcyclicGradlePlugin::class.java)

        val extension = project.extensions.getByType(AcyclicGradleExtension::class.java)
        extension.compilationUnits.set(AcyclicEnforcementMode.ENABLED)
        extension.declarations.set(AcyclicEnforcementMode.OPT_IN)
        extension.declarationOrder.set(AcyclicDeclarationOrderMode.BOTTOM_UP)

        val plugin = AcyclicGradlePlugin()
        val options = plugin.applyToCompilation(fakeCompilation(project)).get()

        assertEquals(
            listOf(
                "compilationUnits" to "enabled",
                "declarations" to "opt-in",
                "declarationOrder" to "bottom-up",
            ),
            options.map { it.key to it.value },
        )
    }

    private fun fakeCompilation(project: org.gradle.api.Project): KotlinCompilation<*> {
        val target =
            Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(KotlinTarget::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getProject" -> project
                    "toString" -> "fakeKotlinTarget"
                    else -> unsupported(method.name)
                }
            } as KotlinTarget

        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(KotlinCompilation::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getTarget" -> target
                "toString" -> "fakeKotlinCompilation"
                else -> unsupported(method.name)
            }
        } as KotlinCompilation<*>
    }

    private fun unsupported(methodName: String): Nothing =
        throw UnsupportedOperationException("Unexpected proxy call to $methodName")
}
