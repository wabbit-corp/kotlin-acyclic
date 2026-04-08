// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers

internal object AcyclicErrors : KtDiagnosticsContainer() {
    val FILE_CYCLE =
        KtDiagnosticFactory1<String>(
            "FILE_CYCLE",
            Severity.ERROR,
            SourceElementPositioningStrategies.DEFAULT,
            PsiElement::class,
            AcyclicErrorMessages,
        )

    val DECLARATION_CYCLE =
        KtDiagnosticFactory1<String>(
            "DECLARATION_CYCLE",
            Severity.ERROR,
            SourceElementPositioningStrategies.DEFAULT,
            PsiElement::class,
            AcyclicErrorMessages,
        )

    val DECLARATION_ORDER_VIOLATION =
        KtDiagnosticFactory1<String>(
            "DECLARATION_ORDER_VIOLATION",
            Severity.ERROR,
            SourceElementPositioningStrategies.DEFAULT,
            PsiElement::class,
            AcyclicErrorMessages,
        )

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = AcyclicErrorMessages
}

internal object AcyclicErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("AcyclicErrors") { map ->
        map.put(AcyclicErrors.FILE_CYCLE, "Circular dependency detected between Kotlin files: {0}", CommonRenderers.STRING)
        map.put(AcyclicErrors.DECLARATION_CYCLE, "Circular dependency detected between Kotlin declarations: {0}", CommonRenderers.STRING)
        map.put(
            AcyclicErrors.DECLARATION_ORDER_VIOLATION,
            "Kotlin declaration order violation: {0}",
            CommonRenderers.STRING,
        )
    }
}
