package one.wabbit.acyclic

import org.jetbrains.kotlin.AbstractKtSourceElement

internal data class DependencyEvidence(
    val targetKey: String,
    val source: AbstractKtSourceElement?,
)

internal data class FileNode(
    val key: String,
    val displayName: String,
    val reportSource: AbstractKtSourceElement?,
    val annotationSource: AbstractKtSourceElement?,
    val dependencies: Map<String, DependencyEvidence>,
    val acyclicEnabled: Boolean,
    val allowCompilationUnitCycles: Boolean,
) {
    fun reportSourceFor(cycleKeys: Set<String>): AbstractKtSourceElement? =
        dependencies.values.firstOrNull { it.targetKey in cycleKeys }?.source ?: annotationSource ?: reportSource
}

internal data class FileCycle(
    val nodes: List<FileNode>,
) {
    val nodeKeys: Set<String> = nodes.mapTo(linkedSetOf(), FileNode::key)

    fun cycleKey(): String = nodes.map(FileNode::key).sorted().joinToString("|")

    fun render(): String = nodes.map(FileNode::displayName).sorted().joinToString(" -> ")

    fun isAllowed(): Boolean = nodes.all(FileNode::allowCompilationUnitCycles)
}

internal class DependencyGraph {
    private val nodes: MutableMap<String, FileNode> = linkedMapOf()
    private var cachedCycles: List<FileCycle>? = null

    fun update(node: FileNode) {
        nodes[node.key] = node
        cachedCycles = null
    }

    fun findCycles(): List<FileCycle> =
        cachedCycles ?: computeCycles().also { cycles ->
            cachedCycles = cycles
        }

    private fun computeCycles(): List<FileCycle> {
        val activeNodes = nodes.values.associateBy(FileNode::key)
        val activeEdges =
            activeNodes.mapValues { (_, node) ->
                node.dependencies.keys.filter { it in activeNodes }
            }

        return stronglyConnectedComponents(activeNodes, activeEdges)
            .mapNotNull { component ->
                if (component.size > 1) {
                    FileCycle(component)
                } else {
                    null
                }
            }
    }
}
