// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.acyclic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeclarationGraphTest {
    @Test
    fun `finds self recursion as a declaration cycle`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "a",
                        displayName = "example/A.kt::foo",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("a" to DeclarationDependencyEvidence("a", null)),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                    ),
                ),
            )

        val cycles = graph.findCycles()

        assertEquals(1, cycles.size)
        assertEquals("a", cycles.single().cycleKey())
    }

    @Test
    fun `finds mutual recursion as a declaration cycle`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "a",
                        displayName = "example/A.kt::foo",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("b" to DeclarationDependencyEvidence("b", null)),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                    ),
                    DeclarationNode(
                        key = "b",
                        displayName = "example/A.kt::bar",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("a" to DeclarationDependencyEvidence("a", null)),
                        sourceIndex = 1,
                        acyclicEnabled = false,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                    ),
                ),
            )

        assertEquals(1, graph.findCycles().size)
    }

    @Test
    fun `finds top down order violations`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "a",
                        displayName = "example/A.kt::foo",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("b" to DeclarationDependencyEvidence("b", null)),
                        sourceIndex = 1,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.TOP_DOWN,
                    ),
                    DeclarationNode(
                        key = "b",
                        displayName = "example/A.kt::bar",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = emptyMap(),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.TOP_DOWN,
                    ),
                ),
            )

        val violations = graph.findOrderViolations()

        assertEquals(1, violations.size)
        assertTrue(violations.single().render(AcyclicDeclarationOrder.TOP_DOWN).contains("later declarations are required"))
    }

    @Test
    fun `finds bottom up order violations`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "a",
                        displayName = "example/A.kt::foo",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("b" to DeclarationDependencyEvidence("b", null)),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.BOTTOM_UP,
                    ),
                    DeclarationNode(
                        key = "b",
                        displayName = "example/A.kt::bar",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = emptyMap(),
                        sourceIndex = 1,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.BOTTOM_UP,
                    ),
                ),
            )

        val violations = graph.findOrderViolations()

        assertEquals(1, violations.size)
        assertTrue(violations.single().render(AcyclicDeclarationOrder.BOTTOM_UP).contains("earlier declarations are required"))
    }

    @Test
    fun `allows self recursion when explicitly annotated`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "a",
                        displayName = "example/A.kt::foo",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("a" to DeclarationDependencyEvidence("a", null)),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = true,
                        allowMutualRecursion = false,
                    ),
                ),
            )

        assertTrue(graph.findCycles().single().isAllowed())
    }

    @Test
    fun `requires all nodes in a mutual cycle to opt out`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "a",
                        displayName = "example/A.kt::foo",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("b" to DeclarationDependencyEvidence("b", null)),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = true,
                        declarationOrder = AcyclicDeclarationOrder.TOP_DOWN,
                    ),
                    DeclarationNode(
                        key = "b",
                        displayName = "example/A.kt::bar",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("a" to DeclarationDependencyEvidence("a", null)),
                        sourceIndex = 1,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                    ),
                ),
            )

        assertTrue(!graph.findCycles().single().isAllowed())
    }

    @Test
    fun `suppresses order violations inside an allowed mutual recursion component`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "a",
                        displayName = "example/A.kt::foo",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("b" to DeclarationDependencyEvidence("b", null)),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = true,
                    ),
                    DeclarationNode(
                        key = "b",
                        displayName = "example/A.kt::bar",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("a" to DeclarationDependencyEvidence("a", null)),
                        sourceIndex = 1,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = true,
                        declarationOrder = AcyclicDeclarationOrder.TOP_DOWN,
                    ),
                ),
            )

        assertTrue(graph.findOrderViolations().isEmpty())
    }

    @Test
    fun `suppresses order violations inside any cyclic component`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "a",
                        displayName = "example/A.kt::foo",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("b" to DeclarationDependencyEvidence("b", null)),
                        sourceIndex = 1,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.TOP_DOWN,
                    ),
                    DeclarationNode(
                        key = "b",
                        displayName = "example/A.kt::bar",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("a" to DeclarationDependencyEvidence("a", null)),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.TOP_DOWN,
                    ),
                ),
            )

        assertEquals(1, graph.findCycles().size)
        assertTrue(graph.findOrderViolations().isEmpty())
    }

    @Test
    fun `evaluates order using each source declaration policy`() {
        val graph =
            DeclarationGraph(
                listOf(
                    DeclarationNode(
                        key = "earlier",
                        displayName = "example/A.kt::earlier",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = emptyMap(),
                        sourceIndex = 0,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                    ),
                    DeclarationNode(
                        key = "useEarlier",
                        displayName = "example/A.kt::useEarlier",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("earlier" to DeclarationDependencyEvidence("earlier", null)),
                        sourceIndex = 1,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.BOTTOM_UP,
                    ),
                    DeclarationNode(
                        key = "useLater",
                        displayName = "example/A.kt::useLater",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = mapOf("later" to DeclarationDependencyEvidence("later", null)),
                        sourceIndex = 2,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.TOP_DOWN,
                    ),
                    DeclarationNode(
                        key = "later",
                        displayName = "example/A.kt::later",
                        reportSource = null,
                        annotationSource = null,
                        dependencies = emptyMap(),
                        sourceIndex = 3,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                    ),
                ),
            )

        assertTrue(graph.findOrderViolations().isEmpty())
    }

    @Test
    fun `handles deep acyclic declaration graphs without recursive SCC traversal`() {
        val nodeCount = 8_000
        val graph =
            DeclarationGraph(
                List(nodeCount) { index ->
                    val key = "node-$index"
                    DeclarationNode(
                        key = key,
                        displayName = "example/A.kt::$key",
                        reportSource = null,
                        annotationSource = null,
                        dependencies =
                            if (index + 1 < nodeCount) {
                                mapOf("node-${index + 1}" to DeclarationDependencyEvidence("node-${index + 1}", null))
                            } else {
                                emptyMap()
                            },
                        sourceIndex = index,
                        acyclicEnabled = true,
                        allowSelfRecursion = false,
                        allowMutualRecursion = false,
                        declarationOrder = AcyclicDeclarationOrder.TOP_DOWN,
                    )
                },
            )

        assertTrue(graph.findCycles().isEmpty())
        assertTrue(graph.findOrderViolations().isEmpty())
    }
}
