package com.adrianczuczka.structural

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StructuralScaleTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `many tracked packages and class exceptions enforce imports across a thousand files`() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("com.adrianczuczka.structural") }
            repositories { mavenCentral() }
        """.trimIndent())

        val packageRules = buildString {
            appendLine("rules:")
            repeat(250) { index ->
                appendLine("  com.app.importers.p$index: []")
                appendLine("  com.app.targets.p$index: []")
            }
        }
        val config = File(projectDir, "structural.yml")
        config.writeText(packageRules + buildString {
            appendLine("classAllowlist:")
            repeat(250) { index ->
                appendLine("  com.app.importers.**.Client$index:")
                appendLine("    - com.app.targets.**.Internal$index")
            }
        })
        repeat(1000) { index ->
            val layer = index % 250
            val batch = index / 250
            File(projectDir, "src/main/java/com/app/importers/p$layer/batch$batch/Client$layer.java").apply {
                parentFile.mkdirs()
                writeText("""
                    package com.app.importers.p$layer.batch$batch;
                    import com.app.targets.p$layer.Internal$layer;
                    public class Client$layer {}
                """.trimIndent())
            }
        }

        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
        val started = System.nanoTime()
        val allowed = runner.build()
        assertThat(allowed.task(":structuralCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(allowed.output).doesNotContain("has no effect")
        println("Scale: 500 tracked packages, 250 class rules, 1000 files checked in " +
            "${(System.nanoTime() - started) / 1_000_000} ms (including Gradle startup).")

        config.writeText(packageRules)
        val denied = runner.buildAndFail()
        assertThat(denied.output).contains("1000 import rule violation(s) detected in 1000 file(s).")
    }
}
