package one.wabbit.acyclic.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FirCompatibilitySourceSetTest {
    @Test
    fun `maps Kotlin 2_3 to the 2_3 compatibility source set`() {
        assertEquals("src/kotlin2_3/kotlin", firCompatibilitySourceDirectoryName("2.3.10"))
    }

    @Test
    fun `maps Kotlin 2_4 prereleases to the 2_4 compatibility source set`() {
        assertEquals("src/kotlin2_4/kotlin", firCompatibilitySourceDirectoryName("2.4.0-Beta1"))
    }

    @Test
    fun `rejects unsupported Kotlin versions instead of silently falling back`() {
        assertFailsWith<IllegalArgumentException> {
            firCompatibilitySourceDirectoryName("2.5.0")
        }
    }
}
