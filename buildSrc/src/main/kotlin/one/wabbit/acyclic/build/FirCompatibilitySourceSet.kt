package one.wabbit.acyclic.build

fun firCompatibilitySourceDirectoryName(kotlinVersion: String): String =
    when {
        kotlinVersion.startsWith("2.3") -> "src/kotlin2_3/kotlin"
        kotlinVersion.startsWith("2.4") -> "src/kotlin2_4/kotlin"
        else -> throw IllegalArgumentException("Unsupported Kotlin version $kotlinVersion")
    }
