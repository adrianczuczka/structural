package com.adrianczuczka.structural.yaml

import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class YamlParserTest {

    @TempDir
    lateinit var tempDir: File

    private fun yaml(content: String): File =
        File(tempDir, "structural.yml").apply { writeText(content.trimIndent()) }

    @Test
    fun `absent classes section yields empty class rule list`() {
        val data = yaml(
            """
            packages:
              - data
              - domain
            rules:
              - data <- domain
            """
        ).parseYamlImportRules()

        assertThat(data?.classRules).isEmpty()
    }

    @Test
    fun `empty classes section yields empty class rule list`() {
        val data = yaml(
            """
            packages:
              - data
              - domain
            rules:
              - data <- domain
            classes: []
            """
        ).parseYamlImportRules()

        assertThat(data?.classRules).isEmpty()
    }

    @Test
    fun `arrow form classes parse to rules`() {
        val data = yaml(
            """
            packages:
              - com.example.api
              - com.example.impl
            rules: []
            classes:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        ).parseYamlImportRules()!!

        assertThat(data.classRules).hasSize(1)
        val rule = data.classRules.single()
        assertThat(rule.importer.packagePattern.pattern).isEqualTo("com.example.api.**")
        assertThat(rule.importer.classPattern).isNull()
        assertThat(rule.imported.packagePattern.pattern).isEqualTo("com.example.impl")
        assertThat(rule.imported.classPattern?.pattern).isEqualTo("FusionException")
    }

    @Test
    fun `right arrow class rule swaps importer and imported`() {
        val data = yaml(
            """
            packages:
              - com.example.api
              - com.example.impl
            rules: []
            classes:
              - "com.example.impl.FusionException -> com.example.api.**"
            """
        ).parseYamlImportRules()!!

        // -> means: right side imports left side
        val rule = data.classRules.single()
        assertThat(rule.importer.packagePattern.pattern).isEqualTo("com.example.api.**")
        assertThat(rule.imported.packagePattern.pattern).isEqualTo("com.example.impl")
        assertThat(rule.imported.classPattern?.pattern).isEqualTo("FusionException")
    }

    @Test
    fun `map form classes parse to rules with importer as key`() {
        val data = yaml(
            """
            packages:
              - com.example.api
              - com.example.impl
            rules: []
            classes:
              "com.example.api.**":
                - com.example.impl.FusionException
                - com.example.impl._Private_*
            """
        ).parseYamlImportRules()!!

        assertThat(data.classRules).hasSize(2)
        data.classRules.forEach { rule ->
            assertThat(rule.importer.packagePattern.pattern).isEqualTo("com.example.api.**")
        }
        val importedClasses = data.classRules.map { it.imported.classPattern?.pattern }
        assertThat(importedClasses).containsExactly("FusionException", "_Private_*")
    }

    @Test
    fun `map and arrow forms produce equivalent rules`() {
        val arrow = yaml(
            """
            packages:
              - com.example.api
              - com.example.impl
            rules: []
            classes:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        ).parseYamlImportRules()!!

        // Reset by writing a different file
        val map = File(tempDir, "structural-map.yml").apply {
            writeText(
                """
                packages:
                  - com.example.api
                  - com.example.impl
                rules: []
                classes:
                  "com.example.api.**":
                    - com.example.impl.FusionException
                """.trimIndent()
            )
        }.parseYamlImportRules()!!

        assertThat(arrow.classRules).isEqualTo(map.classRules)
    }

    @Test
    fun `class rule without arrow is rejected`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                packages:
                  - com.example.api
                  - com.example.impl
                rules: []
                classes:
                  - "com.example.api.**"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("arrows")
    }

    @Test
    fun `classes only with no rules section is allowed`() {
        val data = yaml(
            """
            packages:
              - com.example.api
              - com.example.impl
            classes:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        ).parseYamlImportRules()!!

        assertThat(data.classRules).hasSize(1)
    }

    @Test
    fun `missing both rules and classes is rejected`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                packages:
                  - data
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("rules or classes")
    }
}
