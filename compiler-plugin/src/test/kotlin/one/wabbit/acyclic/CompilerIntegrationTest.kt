package one.wabbit.acyclic

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CompilerIntegrationTest {
    @Test
    fun `sealed nested implementation is treated as scoping not a declaration cycle`() {
        val result =
            compileSnippet(
                """
                package sample

                sealed interface Foo {
                    class Boo : Foo
                }
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `self return type inside a class is treated as scoping not recursion`() {
        val result =
            compileSnippet(
                """
                package sample

                class Foo {
                    fun self(): Foo = this
                }
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `delegated properties do not create synthetic self recursion`() {
        val result =
            compileSnippet(
                """
                package sample

                class Foo {
                    val text by lazy { render() }

                    fun render(): String = "ok"
                }
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `generic type arguments contribute declaration order dependencies`() {
        val result =
            compileSnippet(
                """
                package sample

                fun use(values: List<Later>): Int = values.size

                class Later
                """.trimIndent(),
                declarations = "enabled",
                declarationOrder = "bottom-up",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::use(")
        assertContains(result.renderedMessages(), "sample/Test.kt::Later")
        assertContains(result.renderedMessages(), "earlier declarations are required")
    }

    @Test
    fun `function types contribute declaration order dependencies`() {
        val result =
            compileSnippet(
                """
                package sample

                fun use(factory: () -> Later): Later = factory()

                class Later
                """.trimIndent(),
                declarations = "enabled",
                declarationOrder = "bottom-up",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::use(")
        assertContains(result.renderedMessages(), "sample/Test.kt::Later")
        assertContains(result.renderedMessages(), "earlier declarations are required")
    }

    @Test
    fun `function types contribute compilation unit dependencies`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/A.kt" to
                            """
                            package sample

                            class A(val factory: () -> B)
                            """.trimIndent(),
                        "sample/B.kt" to
                            """
                            package sample

                            class B(val value: A)
                            """.trimIndent(),
                    ),
                declarations = "disabled",
                compilationUnits = "enabled",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertEquals(2, result.errorCountContaining("Circular dependency detected between Kotlin files"))
        assertContains(result.renderedMessages(), "sample/A.kt")
        assertContains(result.renderedMessages(), "sample/B.kt")
    }

    @Test
    fun `member property initializers participate in class construction cycles`() {
        val result =
            compileSnippet(
                """
                package sample

                class A {
                    val next: A = seed()
                }

                fun seed(): A = A()
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::A")
        assertContains(result.renderedMessages(), "sample/Test.kt::seed()")
        assertContains(result.renderedMessages(), "Circular dependency detected between Kotlin declarations")
    }

    @Test
    fun `enum companion helpers do not create declaration cycles`() {
        val result =
            compileSnippet(
                """
                package sample

                import java.util.EnumSet

                enum class Access(val mask: Int) {
                    Public(0x01),
                    Final(0x10);

                    companion object {
                        fun fromMask(mask: Int): EnumSet<Access> {
                            val set = EnumSet.noneOf(Access::class.java)
                            for (access in entries) {
                                if ((mask and access.mask) != 0) {
                                    set.add(access)
                                }
                            }
                            return set
                        }
                    }
                }
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `mutual recursion still fails declaration acyclicity`() {
        val result =
            compileSnippet(
                """
                package sample

                fun a(): Int = b()

                fun b(): Int = a()
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::a()")
        assertContains(result.renderedMessages(), "sample/Test.kt::b()")
    }

    @Test
    fun `allow self recursion permits explicit self recursion`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            import one.wabbit.acyclic.AllowSelfRecursion

                            @AllowSelfRecursion
                            fun loop(n: Int): Int =
                                if (n <= 0) 0 else loop(n - 1)
                            """.trimIndent(),
                    ),
                declarations = "enabled",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `allow mutual recursion requires every declaration in the cycle to opt out`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            import one.wabbit.acyclic.AllowMutualRecursion

                            @AllowMutualRecursion
                            fun a(): Int = b()

                            fun b(): Int = a()
                            """.trimIndent(),
                    ),
                declarations = "enabled",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertEquals(2, result.errorCountContaining("Circular dependency detected between Kotlin declarations"))
        assertContains(result.renderedMessages(), "sample/Test.kt::a()")
        assertContains(result.renderedMessages(), "sample/Test.kt::b()")
    }

    @Test
    fun `allow mutual recursion still fails when only the later declaration opts out`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            import one.wabbit.acyclic.AllowMutualRecursion

                            fun a(): Int = b()

                            @AllowMutualRecursion
                            fun b(): Int = a()
                            """.trimIndent(),
                    ),
                declarations = "enabled",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertEquals(2, result.errorCountContaining("Circular dependency detected between Kotlin declarations"))
        assertContains(result.renderedMessages(), "sample/Test.kt::a()")
        assertContains(result.renderedMessages(), "sample/Test.kt::b()")
    }

    @Test
    fun `allow mutual recursion permits explicit mutual recursion`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            import one.wabbit.acyclic.AllowMutualRecursion

                            @AllowMutualRecursion
                            fun a(): Int = b()

                            @AllowMutualRecursion
                            fun b(): Int = a()
                            """.trimIndent(),
                    ),
                declarations = "enabled",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `allow compilation unit cycles permits explicit inter-file cycle`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/A.kt" to
                            """
                            @file:one.wabbit.acyclic.AllowCompilationUnitCycles

                            package sample

                            fun a(): Int = b()
                            """.trimIndent(),
                        "sample/B.kt" to
                            """
                            @file:one.wabbit.acyclic.AllowCompilationUnitCycles

                            package sample

                            fun b(): Int = a()
                            """.trimIndent(),
                    ),
                declarations = "disabled",
                compilationUnits = "enabled",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `allow compilation unit cycles still fails when only one file opts out`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/A.kt" to
                            """
                            package sample

                            fun a(): Int = b()
                            """.trimIndent(),
                        "sample/B.kt" to
                            """
                            @file:one.wabbit.acyclic.AllowCompilationUnitCycles

                            package sample

                            fun b(): Int = a()
                            """.trimIndent(),
                    ),
                declarations = "disabled",
                compilationUnits = "enabled",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertEquals(2, result.errorCountContaining("Circular dependency detected between Kotlin files"))
        assertContains(result.renderedMessages(), "sample/A.kt")
        assertContains(result.renderedMessages(), "sample/B.kt")
    }

    @Test
    fun `file opt in enables compilation unit checking in opt in mode`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/A.kt" to
                            """
                            @file:one.wabbit.acyclic.Acyclic

                            package sample

                            fun a(): Int = b()
                            """.trimIndent(),
                        "sample/B.kt" to
                            """
                            package sample

                            fun b(): Int = a()
                            """.trimIndent(),
                    ),
                declarations = "disabled",
                compilationUnits = "opt-in",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/A.kt")
        assertContains(result.renderedMessages(), "sample/B.kt")
    }

    @Test
    fun `declaration opt in enables declaration checking in opt in mode`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            import one.wabbit.acyclic.Acyclic

                            @Acyclic
                            fun loop(): Int = loop()
                            """.trimIndent(),
                    ),
                declarations = "opt-in",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::loop()")
    }

    @Test
    fun `file level order override wins over module default`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            @file:one.wabbit.acyclic.Acyclic(
                                order = one.wabbit.acyclic.AcyclicOrder.TOP_DOWN,
                            )

                            package sample

                            fun use(): Int = helper()

                            fun helper(): Int = 1
                            """.trimIndent(),
                    ),
                declarations = "opt-in",
                declarationOrder = "bottom-up",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `declaration level order override wins over module default`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            import one.wabbit.acyclic.Acyclic
                            import one.wabbit.acyclic.AcyclicOrder

                            fun helper(): Int = 1

                            @Acyclic(order = AcyclicOrder.BOTTOM_UP)
                            fun use(): Int = helper()
                            """.trimIndent(),
                    ),
                declarations = "opt-in",
                declarationOrder = "top-down",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `mixed declaration order policies are evaluated per source declaration`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            import one.wabbit.acyclic.Acyclic
                            import one.wabbit.acyclic.AcyclicOrder

                            fun earlier(): Int = 1

                            @Acyclic(order = AcyclicOrder.BOTTOM_UP)
                            fun useEarlier(): Int = earlier()

                            fun useLater(): Int = later()

                            fun later(): Int = 1
                            """.trimIndent(),
                    ),
                declarations = "enabled",
                declarationOrder = "top-down",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `declaration order DEFAULT resets to the module default under a file override`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            @file:one.wabbit.acyclic.Acyclic(
                                order = one.wabbit.acyclic.AcyclicOrder.TOP_DOWN,
                            )

                            package sample

                            import one.wabbit.acyclic.Acyclic
                            import one.wabbit.acyclic.AcyclicOrder

                            fun earlier(): Int = 1

                            @Acyclic(order = AcyclicOrder.DEFAULT)
                            fun useEarlier(): Int = earlier()
                            """.trimIndent(),
                    ),
                declarations = "opt-in",
                declarationOrder = "bottom-up",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `bare Acyclic inherits the file level order override`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            @file:one.wabbit.acyclic.Acyclic(
                                order = one.wabbit.acyclic.AcyclicOrder.TOP_DOWN,
                            )

                            package sample

                            import one.wabbit.acyclic.Acyclic

                            fun earlier(): Int = 1

                            @Acyclic
                            fun useEarlier(): Int = earlier()
                            """.trimIndent(),
                    ),
                declarations = "opt-in",
                declarationOrder = "bottom-up",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::useEarlier()")
        assertContains(result.renderedMessages(), "sample/Test.kt::earlier()")
        assertContains(result.renderedMessages(), "later declarations are required")
    }

    @Test
    fun `typealias can opt into declaration checking`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            import one.wabbit.acyclic.Acyclic

                            @Acyclic
                            typealias Name = String
                            """.trimIndent(),
                    ),
                declarations = "opt-in",
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `local declarations do not block top level declaration analysis`() {
        val result =
            compileSnippet(
                """
                package sample

                fun helper(): Int = 1

                fun use(): Int {
                    fun loop(): Int = loop()
                    return helper()
                }
                """.trimIndent(),
                declarations = "enabled",
                declarationOrder = "top-down",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::use()")
        assertContains(result.renderedMessages(), "sample/Test.kt::helper()")
        assertContains(result.renderedMessages(), "later declarations are required")
    }

    @Test
    fun `local declarations are outside declaration analysis scope`() {
        val result =
            compileSnippet(
                """
                package sample

                fun outer(): Int {
                    fun loop(): Int = loop()
                    return loop()
                }
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `nested class members are analyzed once the full file is complete`() {
        val result =
            compileSnippet(
                """
                package sample

                class Outer {
                    class Inner {
                        fun a(): Int = b()
                        fun b(): Int = a()
                    }
                }
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::Outer.Inner.a()")
        assertContains(result.renderedMessages(), "sample/Test.kt::Outer.Inner.b()")
    }

    @Test
    fun `order diagnostics distinguish overload signatures`() {
        val result =
            compileSnippet(
                """
                package sample

                fun readShort(order: Int): Int = order

                fun readShort(): Int = readShort(0)
                """.trimIndent(),
                declarations = "enabled",
                declarationOrder = "top-down",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample/Test.kt::readShort()")
        assertContains(result.renderedMessages(), "sample/Test.kt::readShort(Int)")
        assertContains(result.renderedMessages(), "later declarations are required")
    }

    @Test
    fun `top down order accepts forward references and bottom up rejects them`() {
        val source =
            """
            package sample

            fun use(): Int = helper()

            fun helper(): Int = 1
            """.trimIndent()

        val topDown =
            compileSnippet(
                source,
                declarations = "enabled",
                declarationOrder = "top-down",
            )
        val bottomUp =
            compileSnippet(
                source,
                declarations = "enabled",
                declarationOrder = "bottom-up",
            )

        assertEquals(ExitCode.OK, topDown.exitCode, topDown.renderedMessages())
        assertEquals(ExitCode.COMPILATION_ERROR, bottomUp.exitCode, bottomUp.renderedMessages())
        assertContains(bottomUp.renderedMessages(), "sample/Test.kt::use()")
        assertContains(bottomUp.renderedMessages(), "sample/Test.kt::helper()")
        assertContains(bottomUp.renderedMessages(), "earlier declarations are required")
    }

    @Test
    fun `class init blocks participate in declaration cycles`() {
        val result =
            compileSnippet(
                """
                package sample

                class A {
                    init {
                        b()
                    }
                }

                fun b(): A = A()
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertEquals(2, result.errorCountContaining("Circular dependency detected between Kotlin declarations"))
        assertContains(result.renderedMessages(), "sample/Test.kt::A")
        assertContains(result.renderedMessages(), "sample/Test.kt::b()")
    }

    @Test
    fun `secondary constructor bodies participate in declaration cycles`() {
        val result =
            compileSnippet(
                """
                package sample

                class A {
                    constructor() {
                        b()
                    }
                }

                fun b(): A = A()
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertEquals(2, result.errorCountContaining("Circular dependency detected between Kotlin declarations"))
        assertContains(result.renderedMessages(), "sample/Test.kt::A")
        assertContains(result.renderedMessages(), "sample/Test.kt::b()")
    }

    @Test
    fun `secondary constructor delegation participates in declaration cycles`() {
        val result =
            compileSnippet(
                """
                package sample

                class A private constructor(
                    val n: Int,
                ) {
                    constructor() : this(seed())
                }

                fun seed(): Int = A().n
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertEquals(2, result.errorCountContaining("Circular dependency detected between Kotlin declarations"))
        assertContains(result.renderedMessages(), "sample/Test.kt::A")
        assertContains(result.renderedMessages(), "sample/Test.kt::seed()")
    }

    @Test
    fun `plain this delegation does not create a declaration self cycle`() {
        val result =
            compileSnippet(
                """
                package sample

                class A private constructor(
                    val n: Int,
                ) {
                    constructor() : this(0)
                }
                """.trimIndent(),
                declarations = "enabled",
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `bottom up order accepts backward references and top down rejects them`() {
        val source =
            """
            package sample

            fun helper(): Int = 1

            fun use(): Int = helper()
            """.trimIndent()

        val bottomUp =
            compileSnippet(
                source,
                declarations = "enabled",
                declarationOrder = "bottom-up",
            )
        val topDown =
            compileSnippet(
                source,
                declarations = "enabled",
                declarationOrder = "top-down",
            )

        assertEquals(ExitCode.OK, bottomUp.exitCode, bottomUp.renderedMessages())
        assertEquals(ExitCode.COMPILATION_ERROR, topDown.exitCode, topDown.renderedMessages())
        assertContains(topDown.renderedMessages(), "sample/Test.kt::use()")
        assertContains(topDown.renderedMessages(), "sample/Test.kt::helper()")
        assertContains(topDown.renderedMessages(), "later declarations are required")
    }

    @Test
    fun `constructor calls to the enclosing class do not create bogus order violations`() {
        val result =
            compileSnippet(
                """
                package sample

                class Foo {
                    fun make(): Foo = Foo()
                }
                """.trimIndent(),
                declarations = "enabled",
                declarationOrder = "top-down",
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `later duplicate plugin options override earlier defaults`() {
        val result =
            compileSources(
                sources =
                    mapOf(
                        "sample/Test.kt" to
                            """
                            package sample

                            fun use(): Int = helper()

                            fun helper(): Int = 1
                            """.trimIndent(),
                    ),
                declarations = "disabled",
                declarationOrder = "none",
                compilationUnits = "disabled",
                extraPluginOptions =
                    listOf(
                        "plugin:$ACYCLIC_PLUGIN_ID:compilationUnits=enabled",
                        "plugin:$ACYCLIC_PLUGIN_ID:declarations=enabled",
                        "plugin:$ACYCLIC_PLUGIN_ID:declarationOrder=top-down",
                    ),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }
}

private data class CompileResult(
    val exitCode: ExitCode,
    val messages: List<String>,
) {
    fun renderedMessages(): String = messages.joinToString(separator = "\n")

    fun errorMessages(): List<String> =
        messages.filter { message -> message.startsWith("${CompilerMessageSeverity.ERROR}:") }

    fun errorCountContaining(fragment: String): Int =
        errorMessages().count { message -> message.contains(fragment) }
}

private fun compileSnippet(
    source: String,
    declarations: String,
    declarationOrder: String = "none",
    compilationUnits: String = "disabled",
): CompileResult =
    compileSources(
        sources = mapOf("sample/Test.kt" to source),
        declarations = declarations,
        declarationOrder = declarationOrder,
        compilationUnits = compilationUnits,
    )

private fun compileSources(
    sources: Map<String, String>,
    declarations: String,
    declarationOrder: String = "none",
    compilationUnits: String = "disabled",
    includeAnnotationDefinitions: Boolean = false,
    extraPluginOptions: List<String> = emptyList(),
): CompileResult {
    val tempDir = Files.createTempDirectory("acyclic-plugin-test")
    try {
        val sourceRoot = tempDir.resolve("src").createDirectories()
        val outputRoot = tempDir.resolve("out").createDirectories()
        val materializedSources =
            buildMap {
                putAll(sources)
                if (includeAnnotationDefinitions) {
                    putAll(libraryAnnotationDefinitions())
                }
            }
        val sourceFiles =
            materializedSources.map { (relativePath, content) ->
                val sourceFile = sourceRoot.resolve(relativePath)
                sourceFile.parent.createDirectories()
                sourceFile.writeText(content)
                sourceFile
            }

        val arguments =
            K2JVMCompilerArguments().apply {
                freeArgs = sourceFiles.map(Path::toString)
                destination = outputRoot.toString()
                classpath = System.getProperty("java.class.path")
                noStdlib = true
                noReflect = true
                pluginClasspaths =
                    arrayOf(
                        pluginClassesDirectory().toString(),
                        pluginResourcesDirectory().toString(),
                    )
                pluginOptions =
                    buildList {
                        add("plugin:$ACYCLIC_PLUGIN_ID:compilationUnits=$compilationUnits")
                        add("plugin:$ACYCLIC_PLUGIN_ID:declarations=$declarations")
                        add("plugin:$ACYCLIC_PLUGIN_ID:declarationOrder=$declarationOrder")
                        addAll(extraPluginOptions)
                    }.toTypedArray()
                jvmTarget = "21"
            }

        val collector = CollectingMessageCollector()
        val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, arguments)
        return CompileResult(exitCode, collector.messages)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}

private fun libraryAnnotationDefinitions(): Map<String, String> {
    val annotationRoot = repositoryRoot().resolve("library/src/commonMain/kotlin/one/wabbit/acyclic")
    return Files.list(annotationRoot).use { paths ->
        paths
            .filter { path -> path.fileName.toString().endsWith(".kt") }
            .toList()
            .sortedBy { path -> path.fileName.toString() }
            .associate { path ->
                "one/wabbit/acyclic/${path.fileName}" to path.readText()
            }
    }
}

private fun repositoryRoot(): Path =
    generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { current -> current.parent }
        .firstOrNull { current -> Files.isDirectory(current.resolve("library/src/commonMain/kotlin/one/wabbit/acyclic")) }
        ?: error("Could not locate kotlin-acyclic repository root from ${System.getProperty("user.dir")}")

private fun pluginClassesDirectory(): Path =
    Path.of(System.getProperty("user.dir"), "build", "classes", "kotlin", "main")

private fun pluginResourcesDirectory(): Path =
    Path.of(System.getProperty("user.dir"), "build", "resources", "main")

private class CollectingMessageCollector : MessageCollector {
    private val _messages = mutableListOf<String>()

    val messages: List<String>
        get() = _messages

    override fun clear() {
        _messages.clear()
    }

    override fun hasErrors(): Boolean =
        _messages.any { message -> message.startsWith("${CompilerMessageSeverity.ERROR}:") }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        if (severity == CompilerMessageSeverity.LOGGING) {
            return
        }
        val locationPrefix =
            location?.let { current ->
                buildString {
                    append(current.path)
                    if (current.line > 0) {
                        append(':')
                        append(current.line)
                    }
                    if (current.column > 0) {
                        append(':')
                        append(current.column)
                    }
                    append(": ")
                }
            }.orEmpty()
        _messages += "$severity: $locationPrefix$message"
    }
}
