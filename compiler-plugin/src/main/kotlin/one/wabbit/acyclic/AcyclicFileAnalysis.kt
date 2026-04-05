package one.wabbit.acyclic

import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeAliasChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.getContainingFile
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classLikeLookupTagIfAny
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid

internal class AcyclicCheckersExtension(
    session: FirSession,
    private val configuration: AcyclicConfiguration,
) : FirAdditionalCheckersExtension(session) {
    private val compilationUnitAnalyzer = CompilationUnitAnalyzer(session, configuration)
    private val declarationAnalyzer = DeclarationNodeAnalyzer(session, configuration)
    private val compilationUnitGraph = DependencyGraph()
    private val reportedFileDiagnostics: MutableSet<FileCycleDiagnosticKey> = linkedSetOf()
    private val declarationStatesByFile: MutableMap<String, DeclarationFileState> = linkedMapOf()
    // Compilation-unit analysis intentionally accumulates cross-file state within one FIR session.
    // Guard it so session-local parallel checker execution cannot race these structures.
    private val compilationUnitLock = Any()
    private val declarationLock = Any()

    private val fileChecker =
        object : FirDeclarationChecker<FirFile>(MppCheckerKind.Common) {
            context(context: CheckerContext, reporter: DiagnosticReporter)
            override fun check(declaration: FirFile) {
                synchronized(compilationUnitLock) {
                    val fileNode = compilationUnitAnalyzer.analyze(declaration)
                    compilationUnitGraph.update(fileNode)
                    reportFileCycles(fileNode, context, reporter)
                }
            }
        }

    private val regularClassChecker =
        object : FirRegularClassChecker(MppCheckerKind.Common) {
            context(context: CheckerContext, reporter: DiagnosticReporter)
            override fun check(declaration: FirRegularClass) {
                checkDeclaration(declaration, context, reporter)
            }
        }

    private val simpleFunctionChecker =
        object : FirDeclarationChecker<FirAcyclicFunctionDeclaration>(MppCheckerKind.Common) {
            context(context: CheckerContext, reporter: DiagnosticReporter)
            override fun check(declaration: FirAcyclicFunctionDeclaration) {
                checkDeclaration(declaration, context, reporter)
            }
        }

    private val propertyChecker =
        object : FirPropertyChecker(MppCheckerKind.Common) {
            context(context: CheckerContext, reporter: DiagnosticReporter)
            override fun check(declaration: FirProperty) {
                checkDeclaration(declaration, context, reporter)
            }
        }

    private val typeAliasChecker =
        object : FirTypeAliasChecker(MppCheckerKind.Common) {
            context(context: CheckerContext, reporter: DiagnosticReporter)
            override fun check(declaration: FirTypeAlias) {
                checkDeclaration(declaration, context, reporter)
            }
        }

    override val declarationCheckers: DeclarationCheckers =
        object : DeclarationCheckers() {
            override val fileCheckers: Set<FirDeclarationChecker<FirFile>> = setOf(fileChecker)
            override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(regularClassChecker)
            override val simpleFunctionCheckers: Set<FirDeclarationChecker<FirAcyclicFunctionDeclaration>> = setOf(simpleFunctionChecker)
            override val propertyCheckers: Set<FirPropertyChecker> = setOf(propertyChecker)
            override val typeAliasCheckers: Set<FirTypeAliasChecker> = setOf(typeAliasChecker)
        }

    private fun reportFileCycles(
        currentNode: FileNode,
        checkerContext: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        val cycles = compilationUnitGraph.findCycles()
        pruneReportedFileDiagnostics(cycles)
        cycles
            .asSequence()
            .filter { cycle -> currentNode.key in cycle.nodeKeys }
            .filter { cycle -> cycle.nodes.any(FileNode::acyclicEnabled) }
            .filterNot(FileCycle::isAllowed)
            .forEach { cycle ->
                cycle.nodes
                    .asSequence()
                    .filter(FileNode::acyclicEnabled)
                    .forEach { reportNode ->
                        val diagnosticKey = FileCycleDiagnosticKey(reportNode.key, cycle.cycleKey())
                        if (!reportedFileDiagnostics.add(diagnosticKey)) {
                            return@forEach
                        }
                        val source = reportNode.reportSourceFor(cycle.nodeKeys) ?: return@forEach
                        with(checkerContext) {
                            reporter.reportOn(source, AcyclicErrors.FILE_CYCLE, cycle.render())
                        }
                    }
            }
    }

    private fun checkDeclaration(
        declaration: FirDeclaration,
        checkerContext: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        if (configuration.declarationMode == AcyclicMode.DISABLED) {
            return
        }
        val result = declarationAnalyzer.analyze(declaration, checkerContext) ?: return
        synchronized(declarationLock) {
            val state =
                declarationStatesByFile.getOrPut(result.fileKey) {
                    DeclarationFileState(expectedNodeKeys = trackedDeclarationKeys(result.containingFile))
                }
            state.nodesByKey[result.node.key] = result.node
            if (state.reported || !state.nodesByKey.keys.containsAll(state.expectedNodeKeys)) {
                return
            }
            val graph = DeclarationGraph(state.nodesByKey.values)
            reportDeclarationCycles(graph, checkerContext, reporter)
            reportDeclarationOrder(graph, checkerContext, reporter)
            state.reported = true
            declarationStatesByFile.remove(result.fileKey)
        }
    }

    private fun reportDeclarationCycles(
        graph: DeclarationGraph,
        checkerContext: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        graph.findCycles()
            .asSequence()
            .filter { cycle -> cycle.nodes.any(DeclarationNode::acyclicEnabled) }
            .filterNot(DeclarationCycle::isAllowed)
            .forEach { cycle ->
                cycle.nodes
                    .asSequence()
                    .filter(DeclarationNode::acyclicEnabled)
                    .forEach { reportNode ->
                        val source = reportNode.reportSourceFor(cycle.nodeKeys) ?: return@forEach
                        with(checkerContext) {
                            reporter.reportOn(source, AcyclicErrors.DECLARATION_CYCLE, cycle.render())
                        }
                    }
            }
    }

    private fun reportDeclarationOrder(
        graph: DeclarationGraph,
        checkerContext: CheckerContext,
        reporter: DiagnosticReporter,
    ) {
        graph.findOrderViolations()
            .asSequence()
            .filter { violation -> violation.sourceNode.acyclicEnabled }
            .forEach { violation ->
                val source =
                    violation.evidence.source
                        ?: violation.sourceNode.annotationSource
                        ?: violation.sourceNode.reportSource
                        ?: return@forEach
                with(checkerContext) {
                    reporter.reportOn(
                        source,
                        AcyclicErrors.DECLARATION_ORDER_VIOLATION,
                            violation.render(),
                        )
                    }
            }
    }

    private fun pruneReportedFileDiagnostics(cycles: List<FileCycle>) {
        val activeCycleKeys = cycles.mapTo(mutableSetOf(), FileCycle::cycleKey)
        reportedFileDiagnostics.removeIf { diagnosticKey -> diagnosticKey.cycleKey !in activeCycleKeys }
    }
}

private data class FileCycleDiagnosticKey(
    val nodeKey: String,
    val cycleKey: String,
)

private data class DeclarationFileState(
    val expectedNodeKeys: Set<String>,
    val nodesByKey: MutableMap<String, DeclarationNode> = linkedMapOf(),
    var reported: Boolean = false,
)

private class CompilationUnitAnalyzer(
    private val session: FirSession,
    private val configuration: AcyclicConfiguration,
) {
    fun analyze(file: FirFile): FileNode {
        val controls = readControlAnnotations(file.symbol, session)
        val visitor = FileDependencyVisitor(session, file)
        file.accept(visitor)

        return FileNode(
            key = file.fileKey(),
            displayName = file.displayName(),
            reportSource = file.source,
            annotationSource = controls.annotationSource,
            dependencies = visitor.dependencies,
            acyclicEnabled =
                configuration.compilationUnitMode.isEnabled(
                    explicitOptIn = controls.acyclic,
                    explicitOptOut = false,
                ),
            allowCompilationUnitCycles = controls.allowCompilationUnitCycles,
        )
    }
}

private class DeclarationNodeAnalyzer(
    private val session: FirSession,
    private val configuration: AcyclicConfiguration,
) {
    fun analyze(
        declaration: FirDeclaration,
        checkerContext: CheckerContext,
    ): DeclarationAnalysisResult? {
        trackedDeclarationKind(declaration) ?: return null
        if (checkerContext.isLocalTrackedDeclaration(declaration)) {
            return null
        }
        val source = declaration.realSource() ?: return null
        val containingFile = session.firProvider.getContainingFile(declaration.symbol) ?: return null
        val fileControls = readControlAnnotations(containingFile.symbol, session)
        val controls = readControlAnnotations(declaration.symbol, session)
        val lookupKey = declarationLookupKey(declaration) ?: return null
        val fileKey = containingFile.fileKey()

        val parentDeclarations =
            checkerContext.containingDeclarations
                .mapNotNull { symbol -> symbol.toTrackedDeclaration(session) }
                .filter { parent -> parent !== declaration && trackedDeclarationKind(parent) != null }

        val relativePath =
            (parentDeclarations.mapNotNull(::trackedDeclarationPathSegment) + trackedDeclarationPathSegment(declaration)).joinToString(".")
        val ancestorLookupKeys = parentDeclarations.mapNotNull(::declarationLookupKey).toSet()
        val dependencyVisitor =
            SingleDeclarationDependencyVisitor(
                session = session,
                rootDeclaration = declaration,
                fileKey = fileKey,
                rootLookupKey = lookupKey,
                ancestorLookupKeys = ancestorLookupKeys,
            )
        dependencyVisitor.collectFrom(declaration)
        val moduleDeclarationOrder = configuration.declarationOrder
        val fileDeclarationOrder = fileControls.orderOverride.effectiveDeclarationOrder(moduleDeclarationOrder)

        return DeclarationAnalysisResult(
            fileKey = fileKey,
            containingFile = containingFile,
            node =
                DeclarationNode(
                    key = "$fileKey:$lookupKey",
                    displayName = "${containingFile.displayName()}::$relativePath",
                    reportSource = source,
                    annotationSource = controls.annotationSource,
                    dependencies = dependencyVisitor.dependencies,
                    sourceIndex = source.startOffset,
                    acyclicEnabled = declarationEnabled(controls, fileControls, configuration.declarationMode),
                    allowSelfRecursion = controls.allowSelfRecursion || fileControls.allowSelfRecursion,
                    allowMutualRecursion = controls.allowMutualRecursion || fileControls.allowMutualRecursion,
                    declarationOrder = controls.orderOverride.effectiveDeclarationOrder(moduleDeclarationOrder, fileDeclarationOrder),
                ),
        )
    }
}

private data class DeclarationAnalysisResult(
    val fileKey: String,
    val containingFile: FirFile,
    val node: DeclarationNode,
)

private fun AcyclicOrderSelection.effectiveDeclarationOrder(
    moduleDefault: AcyclicDeclarationOrder,
    inheritedDefault: AcyclicDeclarationOrder = moduleDefault,
): AcyclicDeclarationOrder =
    when (this) {
        AcyclicOrderSelection.ABSENT -> inheritedDefault
        AcyclicOrderSelection.DEFAULT -> moduleDefault
        AcyclicOrderSelection.NONE,
        AcyclicOrderSelection.TOP_DOWN,
        AcyclicOrderSelection.BOTTOM_UP,
            -> toDeclarationOrderOrNull() ?: inheritedDefault
    }

@OptIn(DirectDeclarationsAccess::class)
private fun trackedDeclarationKeys(file: FirFile): Set<String> =
    buildSet {
        file.declarations.forEach { declaration ->
            collectTrackedDeclarationKeys(file.fileKey(), declaration)
        }
    }

@OptIn(DirectDeclarationsAccess::class)
private fun MutableSet<String>.collectTrackedDeclarationKeys(
    fileKey: String,
    declaration: FirDeclaration,
) {
    declarationLookupKey(declaration)?.let { lookupKey ->
        add("$fileKey:$lookupKey")
    }
    if (declaration is FirRegularClass) {
        declaration.declarations.forEach { nestedDeclaration ->
            collectTrackedDeclarationKeys(fileKey, nestedDeclaration)
        }
    }
}

@OptIn(org.jetbrains.kotlin.fir.symbols.SymbolInternals::class)
private fun CheckerContext.isLocalTrackedDeclaration(
    declaration: FirDeclaration,
): Boolean =
    containingDeclarations
        .mapNotNull { symbol -> symbol.fir }
        .filter { parent -> parent !== declaration }
        .any(::makesDeclarationLocal)

private fun makesDeclarationLocal(parent: FirDeclaration): Boolean = parent !is FirRegularClass && parent !is FirFile

private class FileDependencyVisitor(
    private val session: FirSession,
    private val file: FirFile,
) : FirDefaultVisitorVoid() {
    private val dependencyEvidence: MutableMap<String, DependencyEvidence> = linkedMapOf()

    val dependencies: Map<String, DependencyEvidence>
        get() = dependencyEvidence

    override fun visitElement(element: FirElement) {
        element.acceptChildren(this)
    }

    override fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference) {
        recordDependency(resolvedNamedReference.resolvedSymbol, resolvedNamedReference.source)
        super.visitResolvedNamedReference(resolvedNamedReference)
    }

    override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier) {
        resolvedQualifier.symbol?.let { recordDependency(it, resolvedQualifier.source) }
        super.visitResolvedQualifier(resolvedQualifier)
    }

    override fun visitResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef) {
        resolvedTypeRef.coneType.forEachReferencedClassLikeSymbol(session) { symbol ->
            recordDependency(symbol, resolvedTypeRef.source)
        }
        super.visitResolvedTypeRef(resolvedTypeRef)
    }

    private fun recordDependency(
        symbol: FirBasedSymbol<*>,
        source: AbstractKtSourceElement?,
    ) {
        val targetFile = session.firProvider.getContainingFile(symbol) ?: return
        val targetKey = targetFile.sourceFile?.path ?: targetFile.displayName()
        if (targetKey == file.fileKey()) {
            return
        }
        dependencyEvidence.putIfAbsent(targetKey, DependencyEvidence(targetKey, source))
    }
}

private class SingleDeclarationDependencyVisitor(
    private val session: FirSession,
    private val rootDeclaration: FirDeclaration,
    private val fileKey: String,
    private val rootLookupKey: String,
    private val ancestorLookupKeys: Set<String>,
) : FirDefaultVisitorVoid() {
    private val dependencyEvidence: MutableMap<String, DeclarationDependencyEvidence> = linkedMapOf()

    val dependencies: Map<String, DeclarationDependencyEvidence>
        get() = dependencyEvidence

    fun collectFrom(declaration: FirDeclaration) {
        when (declaration) {
            is FirRegularClass -> collectFromClass(declaration)
            is FirAcyclicFunctionDeclaration -> collectFromFunction(declaration)
            is FirProperty -> collectFromProperty(declaration)
            is FirTypeAlias -> collectFromTypeAlias(declaration)
            else -> declaration.accept(this)
        }
    }

    override fun visitElement(element: FirElement) {
        element.acceptChildren(this)
    }

    override fun visitDeclaration(declaration: FirDeclaration) {
        if (declaration !== rootDeclaration && trackedDeclarationKind(declaration) != null) {
            return
        }
        declaration.acceptChildren(this)
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun collectFromClass(declaration: FirRegularClass) {
        declaration.annotations.forEach { it.accept(this) }
        declaration.typeParameters.forEach { it.accept(this) }
        declaration.contextParameters.forEach(::visitValueParameterSignature)
        declaration.superTypeRefs.forEach { it.accept(this) }
        declaration.declarations.forEach { nestedDeclaration ->
            when (nestedDeclaration) {
                is FirAnonymousInitializer -> collectFromAnonymousInitializer(nestedDeclaration)
                is FirConstructor -> collectFromConstructor(nestedDeclaration)
                is FirProperty -> collectFromMemberPropertyInitialization(nestedDeclaration)
                else -> Unit
            }
        }
    }

    private fun collectFromFunction(declaration: FirAcyclicFunctionDeclaration) {
        visitCallableSignature(declaration)
        declaration.valueParameters.forEach(::visitValueParameterSignature)
        declaration.body?.accept(this)
    }

    private fun collectFromProperty(declaration: FirProperty) {
        visitCallableSignature(declaration)
        declaration.initializer?.accept(this)
        declaration.delegate?.accept(this)
        declaration.getter?.takeIf(::hasRealSource)?.body?.accept(this)
        declaration.setter?.takeIf(::hasRealSource)?.body?.accept(this)
    }

    private fun collectFromMemberPropertyInitialization(property: FirProperty) {
        property.initializer?.accept(this)
        property.delegate?.accept(this)
    }

    private fun collectFromTypeAlias(declaration: FirTypeAlias) {
        declaration.annotations.forEach { it.accept(this) }
        declaration.typeParameters.forEach { it.accept(this) }
        declaration.expandedTypeRef.accept(this)
    }

    private fun visitCallableSignature(declaration: FirCallableDeclaration) {
        declaration.annotations.forEach { it.accept(this) }
        declaration.typeParameters.forEach { it.accept(this) }
        declaration.receiverParameter?.accept(this)
        declaration.contextParameters.forEach(::visitValueParameterSignature)
        declaration.returnTypeRef.accept(this)
    }

    private fun visitConstructorSignature(constructor: FirConstructor) {
        constructor.annotations.forEach { it.accept(this) }
        constructor.typeParameters.forEach { it.accept(this) }
        constructor.contextParameters.forEach(::visitValueParameterSignature)
        constructor.valueParameters.forEach(::visitValueParameterSignature)
    }

    private fun collectFromConstructor(constructor: FirConstructor) {
        visitConstructorSignature(constructor)
        constructor.delegatedConstructor?.accept(this)
        constructor.body?.accept(this)
    }

    private fun collectFromAnonymousInitializer(initializer: FirAnonymousInitializer) {
        initializer.annotations.forEach { it.accept(this) }
        initializer.body?.accept(this)
    }

    private fun visitValueParameterSignature(parameter: FirValueParameter) {
        parameter.annotations.forEach { it.accept(this) }
        parameter.typeParameters.forEach { it.accept(this) }
        parameter.returnTypeRef.accept(this)
        parameter.defaultValue?.accept(this)
    }

    override fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference) {
        recordDependency(
            symbol = resolvedNamedReference.resolvedSymbol,
            source = resolvedNamedReference.source,
            ignoreAncestorTypeReference = false,
        )
        super.visitResolvedNamedReference(resolvedNamedReference)
    }

    override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier) {
        resolvedQualifier.symbol?.let {
            recordDependency(
                symbol = it,
                source = resolvedQualifier.source,
                ignoreAncestorTypeReference = false,
            )
        }
        super.visitResolvedQualifier(resolvedQualifier)
    }

    override fun visitResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef) {
        resolvedTypeRef.coneType.forEachReferencedClassLikeSymbol(session) { symbol ->
            recordDependency(
                symbol = symbol,
                source = resolvedTypeRef.source,
                ignoreAncestorTypeReference = true,
            )
        }
        super.visitResolvedTypeRef(resolvedTypeRef)
    }

    private fun recordDependency(
        symbol: FirBasedSymbol<*>,
        source: AbstractKtSourceElement?,
        ignoreAncestorTypeReference: Boolean,
    ) {
        val realSource = source.realOrNull() ?: return
        val targetFile = session.firProvider.getContainingFile(symbol) ?: return
        if (targetFile.fileKey() != fileKey) {
            return
        }

        val targetDeclaration = symbol.toTrackedDeclaration(session) ?: return
        val targetLookupKey = declarationLookupKey(targetDeclaration) ?: return
        if (targetLookupKey in ancestorLookupKeys) {
            return
        }
        if (ignoreAncestorTypeReference && targetLookupKey == rootLookupKey) {
            return
        }

        val targetNodeKey = "$fileKey:$targetLookupKey"
        dependencyEvidence.putIfAbsent(
            targetNodeKey,
            DeclarationDependencyEvidence(targetNodeKey, realSource),
        )
    }
}

private fun declarationEnabled(
    declarationControls: ControlAnnotations,
    fileControls: ControlAnnotations,
    mode: AcyclicMode,
): Boolean =
    when {
        declarationControls.acyclic -> mode != AcyclicMode.DISABLED
        else ->
            mode.isEnabled(
                explicitOptIn = fileControls.acyclic,
                explicitOptOut = false,
            )
    }

private fun trackedDeclarationKind(declaration: FirDeclaration): String? =
    when (declaration) {
        is FirRegularClass -> "class"
        is FirAcyclicFunctionDeclaration -> "function"
        is FirProperty -> "property"
        is FirTypeAlias -> "typealias"
        else -> null
    }

private fun trackedDeclarationPathSegment(declaration: FirDeclaration): String? =
    when (declaration) {
        is FirRegularClass -> declaration.name.asString()
        is FirAcyclicFunctionDeclaration -> declaration.displayPathSegment()
        is FirProperty -> declaration.displayPathSegment()
        is FirTypeAlias -> declaration.name.asString()
        else -> null
    }

private fun declarationLookupKey(declaration: FirDeclaration): String? {
    val kind = trackedDeclarationKind(declaration) ?: return null
    val name = trackedDeclarationPathSegment(declaration) ?: return null
    val source = declaration.realSource() ?: return null
    return "${source.startOffset}:${source.endOffset}:$kind:$name"
}

private fun FirDeclaration.realSource(): AbstractKtSourceElement? =
    source?.takeUnless { it.kind is KtFakeSourceElementKind }

private fun AbstractKtSourceElement?.realOrNull(): AbstractKtSourceElement? {
    val source = this ?: return null
    val ktSource = source as? KtSourceElement ?: return source
    return source.takeUnless { ktSource.kind is KtFakeSourceElementKind }
}

private fun hasRealSource(declaration: FirDeclaration): Boolean = declaration.realSource() != null

private fun FirAcyclicFunctionDeclaration.displayPathSegment(): String {
    val receiverPrefix =
        receiverParameter?.typeRef?.renderAcyclicType()
            ?.let { "$it." }
            .orEmpty()
    val parameters = valueParameters.joinToString(", ") { it.returnTypeRef.renderAcyclicType() }
    return "$receiverPrefix${name.asString()}($parameters)"
}

private fun FirProperty.displayPathSegment(): String {
    val receiverPrefix =
        receiverParameter?.typeRef?.renderAcyclicType()
            ?.let { "$it." }
            .orEmpty()
    return "$receiverPrefix${name.asString()}"
}

private fun org.jetbrains.kotlin.fir.types.FirTypeRef.renderAcyclicType(): String =
    (source.realOrNull() as? KtPsiSourceElement)?.psi?.text
        ?: (this as? FirResolvedTypeRef)?.coneType?.renderAcyclicType()
        ?: toString()

private fun ConeKotlinType.renderAcyclicType(): String {
    val classId = classLikeLookupTagIfAny?.classId ?: return toString()
    val baseName = classId.shortClassName.asString()
    return if (isMarkedNullable) "$baseName?" else baseName
}

private fun ConeKotlinType.forEachReferencedClassLikeSymbol(
    session: FirSession,
    visit: (FirBasedSymbol<*>) -> Unit,
) {
    when (val lowered = lowerBoundIfFlexible()) {
        is ConeDefinitelyNotNullType -> lowered.original.forEachReferencedClassLikeSymbol(session, visit)
        is ConeIntersectionType -> lowered.intersectedTypes.forEach { type -> type.forEachReferencedClassLikeSymbol(session, visit) }
        else -> {
            (lowered as? ConeClassLikeType)
                ?.lookupTag
                ?.toSymbol(session)
                ?.let(visit)
            lowered.typeArguments.forEach { argument ->
                argument.type?.forEachReferencedClassLikeSymbol(session, visit)
            }
        }
    }
}

@OptIn(org.jetbrains.kotlin.fir.symbols.SymbolInternals::class)
private fun FirBasedSymbol<*>.toTrackedDeclaration(session: FirSession): FirDeclaration? {
    val fir = fir
    return when (fir) {
        is FirPropertyAccessor -> fir.propertySymbol.fir
        is FirConstructor ->
            fir.returnTypeRef.coneType.classLikeLookupTagIfAny
                ?.toSymbol(session)
                ?.fir

        else -> fir
    }
}

private fun FirFile.displayName(): String =
    if (packageDirective.packageFqName.isRoot) {
        name
    } else {
        "${packageDirective.packageFqName.asString()}/$name"
    }

private fun FirFile.fileKey(): String = sourceFile?.path ?: displayName()
