package one.wabbit.acyclic

private data class DepthFirstFrame(
    val nodeKey: String,
    var nextEdgeIndex: Int = 0,
)

internal fun <T> stronglyConnectedComponents(
    nodesByKey: Map<String, T>,
    edgesByKey: Map<String, List<String>>,
): List<List<T>> {
    if (nodesByKey.isEmpty()) {
        return emptyList()
    }

    val reverseEdges =
        nodesByKey.keys.associateWithTo(linkedMapOf()) { mutableListOf<String>() }
    edgesByKey.forEach { (sourceKey, targetKeys) ->
        targetKeys.forEach { targetKey ->
            if (targetKey in nodesByKey) {
                reverseEdges.getValue(targetKey) += sourceKey
            }
        }
    }

    val visited = mutableSetOf<String>()
    val finishOrder = mutableListOf<String>()

    nodesByKey.keys.forEach { startKey ->
        if (!visited.add(startKey)) {
            return@forEach
        }
        val stack = ArrayDeque<DepthFirstFrame>()
        stack.addFirst(DepthFirstFrame(startKey))

        while (stack.isNotEmpty()) {
            val frame = stack.first()
            val edges = edgesByKey[frame.nodeKey].orEmpty()
            if (frame.nextEdgeIndex < edges.size) {
                val dependencyKey = edges[frame.nextEdgeIndex]
                frame.nextEdgeIndex += 1
                if (dependencyKey in nodesByKey && visited.add(dependencyKey)) {
                    stack.addFirst(DepthFirstFrame(dependencyKey))
                }
            } else {
                stack.removeFirst()
                finishOrder += frame.nodeKey
            }
        }
    }

    val assigned = mutableSetOf<String>()
    val components = mutableListOf<List<T>>()

    finishOrder.asReversed().forEach { startKey ->
        if (!assigned.add(startKey)) {
            return@forEach
        }

        val component = mutableListOf<T>()
        val stack = ArrayDeque<String>()
        stack.addFirst(startKey)

        while (stack.isNotEmpty()) {
            val currentKey = stack.removeFirst()
            component += nodesByKey.getValue(currentKey)
            reverseEdges[currentKey].orEmpty().forEach { dependencyKey ->
                if (assigned.add(dependencyKey)) {
                    stack.addFirst(dependencyKey)
                }
            }
        }

        components += component
    }

    return components
}
