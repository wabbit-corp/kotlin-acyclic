package one.wabbit.acyclic

import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol

internal data class ControlAnnotations(
    val acyclic: Boolean = false,
    val allowCompilationUnitCycles: Boolean = false,
    val allowSelfRecursion: Boolean = false,
    val allowMutualRecursion: Boolean = false,
    val orderOverride: AcyclicOrderSelection = AcyclicOrderSelection.ABSENT,
    val annotationSource: AbstractKtSourceElement? = null,
)

internal enum class AcyclicOrderSelection {
    ABSENT,
    DEFAULT,
    NONE,
    TOP_DOWN,
    BOTTOM_UP,
    ;

    fun toDeclarationOrderOrNull(): AcyclicDeclarationOrder? =
        when (this) {
            ABSENT, DEFAULT -> null
            NONE -> AcyclicDeclarationOrder.NONE
            TOP_DOWN -> AcyclicDeclarationOrder.TOP_DOWN
            BOTTOM_UP -> AcyclicDeclarationOrder.BOTTOM_UP
        }
}

internal fun readControlAnnotations(
    symbol: FirBasedSymbol<*>,
    session: FirSession,
): ControlAnnotations {
    val acyclicAnnotation = symbol.resolvedAnnotationsWithArguments.getAnnotationByClassId(ACYCLIC_ANNOTATION_CLASS_ID, session)
    val allowCompilationUnitCyclesAnnotation =
        symbol.resolvedAnnotationsWithClassIds.getAnnotationByClassId(ALLOW_COMPILATION_UNIT_CYCLES_ANNOTATION_CLASS_ID, session)
    val allowSelfRecursionAnnotation =
        symbol.resolvedAnnotationsWithClassIds.getAnnotationByClassId(ALLOW_SELF_RECURSION_ANNOTATION_CLASS_ID, session)
    val allowMutualRecursionAnnotation =
        symbol.resolvedAnnotationsWithClassIds.getAnnotationByClassId(ALLOW_MUTUAL_RECURSION_ANNOTATION_CLASS_ID, session)

    return ControlAnnotations(
        acyclic = acyclicAnnotation != null,
        allowCompilationUnitCycles = allowCompilationUnitCyclesAnnotation != null,
        allowSelfRecursion = allowSelfRecursionAnnotation != null,
        allowMutualRecursion = allowMutualRecursionAnnotation != null,
        orderOverride = acyclicAnnotation?.extractOrderOverride() ?: AcyclicOrderSelection.ABSENT,
        annotationSource =
            acyclicAnnotation?.source
                ?: allowCompilationUnitCyclesAnnotation?.source
                ?: allowSelfRecursionAnnotation?.source
                ?: allowMutualRecursionAnnotation?.source,
    )
}

private fun org.jetbrains.kotlin.fir.expressions.FirAnnotation.extractOrderOverride(): AcyclicOrderSelection {
    val orderName = findArgumentByName(ACYCLIC_ORDER_ARGUMENT_NAME)?.extractEnumValueArgumentInfo()?.enumEntryName?.asString()
    return when (orderName) {
        null -> AcyclicOrderSelection.ABSENT
        "DEFAULT" -> AcyclicOrderSelection.DEFAULT
        "NONE" -> AcyclicOrderSelection.NONE
        "TOP_DOWN" -> AcyclicOrderSelection.TOP_DOWN
        "BOTTOM_UP" -> AcyclicOrderSelection.BOTTOM_UP
        else -> AcyclicOrderSelection.ABSENT
    }
}
