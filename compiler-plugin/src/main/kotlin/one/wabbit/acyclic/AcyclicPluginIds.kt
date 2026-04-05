package one.wabbit.acyclic

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal const val ACYCLIC_PLUGIN_ID: String = "one.wabbit.acyclic"
internal const val ACYCLIC_ANNOTATION_FQ_NAME: String = "one.wabbit.acyclic.Acyclic"
internal const val ALLOW_COMPILATION_UNIT_CYCLES_ANNOTATION_FQ_NAME: String =
    "one.wabbit.acyclic.AllowCompilationUnitCycles"
internal const val ALLOW_SELF_RECURSION_ANNOTATION_FQ_NAME: String =
    "one.wabbit.acyclic.AllowSelfRecursion"
internal const val ALLOW_MUTUAL_RECURSION_ANNOTATION_FQ_NAME: String =
    "one.wabbit.acyclic.AllowMutualRecursion"
internal const val ACYCLIC_ORDER_FQ_NAME: String = "one.wabbit.acyclic.AcyclicOrder"

internal val ACYCLIC_ANNOTATION_CLASS_ID: ClassId = ClassId.topLevel(FqName(ACYCLIC_ANNOTATION_FQ_NAME))
internal val ALLOW_COMPILATION_UNIT_CYCLES_ANNOTATION_CLASS_ID: ClassId =
    ClassId.topLevel(FqName(ALLOW_COMPILATION_UNIT_CYCLES_ANNOTATION_FQ_NAME))
internal val ALLOW_SELF_RECURSION_ANNOTATION_CLASS_ID: ClassId =
    ClassId.topLevel(FqName(ALLOW_SELF_RECURSION_ANNOTATION_FQ_NAME))
internal val ALLOW_MUTUAL_RECURSION_ANNOTATION_CLASS_ID: ClassId =
    ClassId.topLevel(FqName(ALLOW_MUTUAL_RECURSION_ANNOTATION_FQ_NAME))
internal val ACYCLIC_ORDER_CLASS_ID: ClassId = ClassId.topLevel(FqName(ACYCLIC_ORDER_FQ_NAME))
internal val ACYCLIC_ORDER_ARGUMENT_NAME: Name = Name.identifier("order")
