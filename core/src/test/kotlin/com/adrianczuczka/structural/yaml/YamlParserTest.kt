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
            rules:
              - data <- domain
            classAllowlist: []
            """
        ).parseYamlImportRules()

        assertThat(data?.classRules).isEmpty()
    }

    @Test
    fun `arrow form classes parse to rules`() {
        val data = yaml(
            """
            rules:
              - com.example.api
              - com.example.impl
            classAllowlist:
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
            rules:
              - com.example.api
              - com.example.impl
            classAllowlist:
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
            rules:
              - com.example.api
              - com.example.impl
            classAllowlist:
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
            rules:
              - com.example.api
              - com.example.impl
            classAllowlist:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        ).parseYamlImportRules()!!

        // Reset by writing a different file
        val map = File(tempDir, "structural-map.yml").apply {
            writeText(
                """
                rules:
                  - com.example.api
                  - com.example.impl
                classAllowlist:
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
                rules:
                  - com.example.api
                  - com.example.impl
                classAllowlist:
                  - "com.example.api.**"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("arrows")
    }

    @Test
    fun `bare identifier in arrow form rules tracks the package`() {
        val data = yaml(
            """
            rules:
              - data <- domain
              - legacy
            """
        ).parseYamlImportRules()!!

        val patterns = data.rules.keys.map { it.pattern }
        assertThat(patterns).containsExactly("data", "domain", "legacy")
        assertThat(data.rules[TrackedPackage("legacy")]).isEmpty()
    }

    @Test
    fun `map form value packages become tracked too`() {
        val data = yaml(
            """
            rules:
              data:
                - domain
            """
        ).parseYamlImportRules()!!

        val patterns = data.rules.keys.map { it.pattern }
        assertThat(patterns).containsExactly("data", "domain")
    }

    @Test
    fun `classes only without rules is rejected`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                classAllowlist:
                  - "com.example.api.** <- com.example.impl.FusionException"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("No tracked packages")
    }

    @Test
    fun `legacy packages block triggers a migration error`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                packages:
                  - data
                  - domain
                rules:
                  - data <- domain
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("`packages:` block has been removed")
    }

    @Test
    fun `missing both rules and classAllowlist is rejected`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                # empty config
                empty: true
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("rules or classAllowlist")
    }

    @Test
    fun `legacy classes block triggers a migration error`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - com.example.api
                  - com.example.impl
                classes:
                  - "com.example.api.** <- com.example.impl.FusionException"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("`classes:` block has been renamed to `classAllowlist:`")
    }

    @Test
    fun `class rule with untracked importer package is rejected`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - com.example.foo
                classAllowlist:
                  - "com.example.api.X <- com.example.impl.Y"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("importer package")
        assertThat(ex.message).contains("com.example.api")
        assertThat(ex.message).contains("no rule in `rules:` covers it")
    }

    @Test
    fun `class rule with untracked imported package is rejected`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - com.example.api
                classAllowlist:
                  - "com.example.api.X <- com.example.impl.Y"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("imported package")
        assertThat(ex.message).contains("com.example.impl")
    }

    @Test
    fun `class rule whose sides fall under the same tracked package emits a warning`() {
        val data = yaml(
            """
            rules:
              - com.example.app
            classAllowlist:
              - "com.example.app.api.ApiBuilder <- com.example.app.impl.**"
            """
        ).parseYamlImportRules()!!

        assertThat(data.warnings).hasSize(1)
        assertThat(data.warnings.single()).contains("both sides fall under tracked package")
        assertThat(data.warnings.single()).contains("com.example.app")
    }

    @Test
    fun `class rule redundant with package rule emits a warning`() {
        val data = yaml(
            """
            rules:
              - "com.example.api <- com.example.impl"
            classAllowlist:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        ).parseYamlImportRules()!!

        assertThat(data.warnings).hasSize(1)
        assertThat(data.warnings.single()).contains("package rule already permits")
    }

    @Test
    fun `effective class rule produces no warnings`() {
        val data = yaml(
            """
            rules:
              - com.example.api
              - com.example.impl
            classAllowlist:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        ).parseYamlImportRules()!!

        assertThat(data.warnings).isEmpty()
    }

    @Test
    fun `map-form class rule with untracked imported package is rejected`() {
        // Same validation must run for map-form class rules, not just arrow-form.
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - com.example.api
                classAllowlist:
                  "com.example.api.**":
                    - com.example.impl.FusionException
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("imported package")
        assertThat(ex.message).contains("com.example.impl")
    }

    @Test
    fun `validation rejects when one rule in a multi-rule list is invalid`() {
        // A valid rule preceding an invalid one must still trigger the error;
        // validation must not short-circuit on the first valid rule.
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - com.example.api
                  - com.example.impl
                classAllowlist:
                  - "com.example.api.** <- com.example.impl.FusionException"
                  - "com.example.api.** <- com.example.missing.SomeClass"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("com.example.missing")
    }

    @Test
    fun `validation accepts class rule whose double-star can match a tracked single-segment`() {
        // The `**` in `com.example.api.**` can expand to any segment, so it
        // overlaps with a tracked single-segment `api` (concrete: `com.example.api`).
        // This is a known correct behavior — pinned to prevent regression.
        val data = yaml(
            """
            rules:
              - api
              - impl
            classAllowlist:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        ).parseYamlImportRules()!!

        assertThat(data.warnings).isEmpty()
    }
}
