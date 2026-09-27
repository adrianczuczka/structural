package com.adrianczuczka.structural

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RuleConfigurationTest {
    @TempDir
    lateinit var projectDir: File

    private val importers = mapOf(
        "ResourcePosition" to "dev.ionfusion.commons.resources",
        "CoverageConfiguration" to "dev.ionfusion.runtime._private.cover",
        "FusionException" to "dev.ionfusion.runtime.base",
    )

    @BeforeEach
    fun setup() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("com.adrianczuczka.structural") }
            repositories { mavenCentral() }
        """.trimIndent())
        for ((className, packageName) in importers) {
            File(projectDir, "src/main/java/${packageName.replace('.', '/')}/$className.java").apply {
                parentFile.mkdirs()
                writeText("""
                    package $packageName;
                    import static dev.ionfusion.commons._private.io.Ordinals.displayFriendlyPosition;
                    public class $className {}
                """.trimIndent())
            }
        }
    }

    private fun writeRules(broadTarget: Boolean, ioDeclaration: String, allowIo: Boolean = false) {
        File(projectDir, "structural.yml").writeText(buildString {
            appendLine("rules:")
            for (packageName in importers.values) {
                appendLine("  $packageName:")
                appendLine("    - dev.ionfusion.commons.util")
                if (allowIo) appendLine("    - dev.ionfusion.commons._private.io")
            }
            appendLine("  dev.ionfusion.commons._private.io: $ioDeclaration")
            appendLine("  dev.ionfusion.fusion!:")
            appendLine("    - dev.ionfusion.commons.resources")
            if (broadTarget) appendLine("    - dev.ionfusion.commons.**")
        })
    }

    private fun runner(): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments("structuralCheck")

    @Test
    fun `issue 17 malformed declaration fails before checking imports with or without broad target`() {
        for (broadTarget in listOf(false, true)) {
            writeRules(broadTarget, "// Should not depend on anything")

            val result = runner().buildAndFail()

            assertThat(result.output).contains("Invalid rules value for `dev.ionfusion.commons._private.io`")
            assertThat(result.output).contains("Use []")
            assertThat(result.output).contains("YAML comments start with #, not //")
            assertThat(result.output).doesNotContain("Import rule violations found")
        }
    }

    @Test
    fun `issue 17 corrected declaration enforces the same imports with or without broad target`() {
        for (broadTarget in listOf(false, true)) {
            writeRules(broadTarget, "[] # Should not depend on anything")

            val denied = runner().buildAndFail()

            assertThat(denied.output).contains("3 import rule violation(s) detected in 3 file(s).")
            for (packageName in importers.values) {
                assertThat(denied.output)
                    .contains("`$packageName` cannot import from `dev.ionfusion.commons._private.io`")
            }

            writeRules(broadTarget, "[] # Should not depend on anything", allowIo = true)

            val allowed = runner().build()

            assertThat(allowed.output).contains("All package imports follow the specified package rules")
        }
    }
}
