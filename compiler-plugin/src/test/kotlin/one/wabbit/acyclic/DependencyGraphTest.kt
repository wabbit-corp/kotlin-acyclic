// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.acyclic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyGraphTest {
    @Test
    fun `finds cycles when any file in the strongly connected component enables acyclic`() {
        val graph = DependencyGraph()

        graph.update(
            FileNode(
                key = "a",
                displayName = "example/A.kt",
                reportSource = null,
                annotationSource = null,
                dependencies = mapOf("b" to DependencyEvidence("b", null)),
                acyclicEnabled = true,
                allowCompilationUnitCycles = false,
            ),
        )
        graph.update(
            FileNode(
                key = "b",
                displayName = "example/B.kt",
                reportSource = null,
                annotationSource = null,
                dependencies = mapOf("a" to DependencyEvidence("a", null)),
                acyclicEnabled = false,
                allowCompilationUnitCycles = false,
            ),
        )

        val cycles = graph.findCycles()

        assertEquals(1, cycles.size)
        assertEquals("a|b", cycles.single().cycleKey())
        assertEquals("example/A.kt -> example/B.kt", cycles.single().render())
    }

    @Test
    fun `allows compilation unit cycle only when every file opts out`() {
        val graph = DependencyGraph()

        graph.update(
            FileNode(
                key = "a",
                displayName = "example/A.kt",
                reportSource = null,
                annotationSource = null,
                dependencies = mapOf("b" to DependencyEvidence("b", null)),
                acyclicEnabled = true,
                allowCompilationUnitCycles = true,
            ),
        )
        graph.update(
            FileNode(
                key = "b",
                displayName = "example/B.kt",
                reportSource = null,
                annotationSource = null,
                dependencies = mapOf("a" to DependencyEvidence("a", null)),
                acyclicEnabled = false,
                allowCompilationUnitCycles = true,
            ),
        )

        assertTrue(graph.findCycles().single().isAllowed())
    }

    @Test
    fun `report source prefers the edge inside the cycle`() {
        val graph = DependencyGraph()

        graph.update(
            FileNode(
                key = "a",
                displayName = "example/A.kt",
                reportSource = null,
                annotationSource = null,
                dependencies = mapOf("b" to DependencyEvidence("b", null)),
                acyclicEnabled = true,
                allowCompilationUnitCycles = false,
            ),
        )
        graph.update(
            FileNode(
                key = "b",
                displayName = "example/B.kt",
                reportSource = null,
                annotationSource = null,
                dependencies = mapOf("c" to DependencyEvidence("c", null)),
                acyclicEnabled = false,
                allowCompilationUnitCycles = false,
            ),
        )
        graph.update(
            FileNode(
                key = "c",
                displayName = "example/C.kt",
                reportSource = null,
                annotationSource = null,
                dependencies = mapOf("a" to DependencyEvidence("a", null)),
                acyclicEnabled = false,
                allowCompilationUnitCycles = false,
            ),
        )

        val cycle = graph.findCycles().single()

        assertEquals(setOf("a", "b", "c"), cycle.nodeKeys)
    }

    @Test
    fun `handles deep acyclic file graphs without recursive SCC traversal`() {
        val nodeCount = 8_000
        val graph = DependencyGraph()

        repeat(nodeCount) { index ->
            val key = "file-$index"
            graph.update(
                FileNode(
                    key = key,
                    displayName = "example/$key.kt",
                    reportSource = null,
                    annotationSource = null,
                    dependencies =
                        if (index + 1 < nodeCount) {
                            mapOf("file-${index + 1}" to DependencyEvidence("file-${index + 1}", null))
                        } else {
                            emptyMap()
                        },
                    acyclicEnabled = true,
                    allowCompilationUnitCycles = false,
                ),
            )
        }

        assertTrue(graph.findCycles().isEmpty())
    }
}
