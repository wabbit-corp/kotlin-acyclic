// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic

import org.jetbrains.kotlin.AbstractKtSourceElement

internal data class DeclarationDependencyEvidence(
    val targetKey: String,
    val source: AbstractKtSourceElement?,
)

internal data class DeclarationNode(
    val key: String,
    val displayName: String,
    val reportSource: AbstractKtSourceElement?,
    val annotationSource: AbstractKtSourceElement?,
    val dependencies: Map<String, DeclarationDependencyEvidence>,
    val sourceIndex: Int,
    val acyclicEnabled: Boolean,
    val allowSelfRecursion: Boolean,
    val allowMutualRecursion: Boolean,
    val declarationOrder: AcyclicDeclarationOrder = AcyclicDeclarationOrder.NONE,
) {
    fun reportSourceFor(cycleKeys: Set<String>): AbstractKtSourceElement? =
        dependencies.values.firstOrNull { it.targetKey in cycleKeys }?.source ?: annotationSource ?: reportSource
}

internal data class DeclarationCycle(
    val nodes: List<DeclarationNode>,
    val hasSelfLoop: Boolean,
) {
    val nodeKeys: Set<String> = nodes.mapTo(linkedSetOf(), DeclarationNode::key)

    fun cycleKey(): String = nodes.map(DeclarationNode::key).sorted().joinToString("|")

    fun render(): String = nodes.map(DeclarationNode::displayName).sorted().joinToString(" -> ")

    fun isSelfCycle(): Boolean = nodes.size == 1 && hasSelfLoop

    fun isAllowed(): Boolean =
        when {
            isSelfCycle() -> nodes.single().allowSelfRecursion
            else -> nodes.all(DeclarationNode::allowMutualRecursion)
        }
}

internal data class DeclarationOrderViolation(
    val sourceNode: DeclarationNode,
    val targetNode: DeclarationNode,
    val evidence: DeclarationDependencyEvidence,
    val order: AcyclicDeclarationOrder,
) {
    fun render(order: AcyclicDeclarationOrder = this.order): String {
        val relation =
            when (order) {
                AcyclicDeclarationOrder.TOP_DOWN -> "later"
                AcyclicDeclarationOrder.BOTTOM_UP -> "earlier"
                AcyclicDeclarationOrder.NONE -> "ordered"
            }
        return "${sourceNode.displayName} depends on ${targetNode.displayName}, but $relation declarations are required"
    }
}

internal class DeclarationGraph(
    nodes: Iterable<DeclarationNode>,
) {
    private val nodesByKey: Map<String, DeclarationNode> = nodes.associateBy(DeclarationNode::key)
    private val edgesByKey: Map<String, List<String>> =
        nodesByKey.mapValues { (_, node) ->
            node.dependencies.keys.filter { it in nodesByKey }
        }
    private val cycles: List<DeclarationCycle> by lazy {
        stronglyConnectedComponents(nodesByKey, edgesByKey)
            .mapNotNull { component ->
                val hasSelfLoop =
                    component.size == 1 &&
                        edgesByKey[component.single().key].orEmpty().contains(component.single().key)
                if (component.size > 1 || hasSelfLoop) {
                    DeclarationCycle(component, hasSelfLoop)
                } else {
                    null
                }
            }
    }
    private val orderViolations: List<DeclarationOrderViolation> by lazy {
        val cycleKeysByNode =
            cycles
                .asSequence()
                .flatMap { cycle -> cycle.nodeKeys.asSequence().map { key -> key to cycle.cycleKey() } }
                .toMap()
        buildList {
            nodesByKey.values.forEach { node ->
                if (node.declarationOrder == AcyclicDeclarationOrder.NONE) {
                    return@forEach
                }
                node.dependencies.values.forEach { dependency ->
                    val targetNode = nodesByKey[dependency.targetKey] ?: return@forEach
                    if (node.key == targetNode.key) {
                        return@forEach
                    }
                    if (
                        cycleKeysByNode[node.key] != null &&
                        cycleKeysByNode[node.key] == cycleKeysByNode[targetNode.key]
                    ) {
                        return@forEach
                    }
                    val isOrdered =
                        when (node.declarationOrder) {
                            AcyclicDeclarationOrder.TOP_DOWN -> node.sourceIndex < targetNode.sourceIndex
                            AcyclicDeclarationOrder.BOTTOM_UP -> node.sourceIndex > targetNode.sourceIndex
                            AcyclicDeclarationOrder.NONE -> true
                        }
                    if (!isOrdered) {
                        add(DeclarationOrderViolation(node, targetNode, dependency, node.declarationOrder))
                    }
                }
            }
        }
    }

    fun findCycles(): List<DeclarationCycle> = cycles

    fun findOrderViolations(): List<DeclarationOrderViolation> = orderViolations
}
