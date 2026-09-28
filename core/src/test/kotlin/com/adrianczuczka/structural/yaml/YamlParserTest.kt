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
    fun `equivalent subtree spellings merge grants and preserve either explicit reset`() {
        for (resetKey in listOf("com.app.feature", "com.app.feature.**")) {
            val otherKey = if (resetKey.endsWith(".**")) "com.app.feature" else "com.app.feature.**"
            for (reverse in listOf(false, true)) {
                val entries = listOf(
                    "$resetKey: {inherit: false, allow: [com.lib]}",
                    "$otherKey: [com.extra]",
                ).let { if (reverse) it.reversed() else it }.joinToString("\n")
                val data = yaml("rules:\n  com.app: [com.shared]\n" +
                    entries.prependIndent("  ") + "\n  com.app.feature.child: []").parseYamlImportRules()!!
                assertThat(data.checkedPackages).doesNotContain(TrackedPackage("com.app.feature.**"))
                for (key in listOf("com.app.feature", "com.app.feature.child")) {
                    assertThat(data.rules[TrackedPackage(key)])
                        .containsExactly(TrackedPackage("com.lib"), TrackedPackage("com.extra"))
                }
            }
        }
    }

    @Test
    fun `composite and object entries without inherit preserve explicit reset in either order`() {
        for (value in listOf("[com.lib]", "{allow: [com.lib]}")) {
            for (reverse in listOf(false, true)) {
                val entries = listOf(
                    "com.app.core: {inherit: false, allow: []}",
                    "? [com.app.core, com.app.ui]\n: $value",
                ).let { if (reverse) it.reversed() else it }.joinToString("\n")
                val data = yaml("rules:\n  com.app: [com.shared]\n" + entries.prependIndent("  "))
                    .parseYamlImportRules()!!
                assertThat(data.rules[TrackedPackage("com.app.core")]).containsExactly(TrackedPackage("com.lib"))
                assertThat(data.rules[TrackedPackage("com.app.ui")])
                    .containsExactly(TrackedPackage("com.shared"), TrackedPackage("com.lib"))
            }
        }
    }

    @Test
    fun `equivalent declarations reject only conflicting explicit inheritance settings`() {
        for (setting in listOf(true, false)) {
            val error = assertThrows<GradleException> {
                yaml("""
                    rules:
                      com.app: {inherit: $setting, allow: []}
                      com.app.**: {inherit: ${!setting}, allow: []}
                """).parseYamlImportRules()
            }
            assertThat(error.message).contains("Conflicting inherit settings")
        }
        val data = yaml("""
            rules:
              com.app: {inherit: false, allow: [com.lib]}
              com.app.**: {inherit: false, allow: [com.extra]}
        """).parseYamlImportRules()!!
        assertThat(data.rules[TrackedPackage("com.app")])
            .containsExactly(TrackedPackage("com.lib"), TrackedPackage("com.extra"))
    }

    @Test
    fun `arrow targets and importers use canonical subtree names without changing shorthand`() {
        val data = yaml("""
            rules:
              - com.app <- com.lib.**
              - com.app.** <- com.extra
              - com.**
        """).parseYamlImportRules()!!
        assertThat(data.rules[TrackedPackage("com.app")])
            .containsExactly(TrackedPackage("com.lib"), TrackedPackage("com.extra"))
        assertThat(data.checkedPackages).contains(TrackedPackage("com.**"))
        assertThat(data.checkedPackages).doesNotContain(TrackedPackage("com"))
    }

    @Test
    fun `wildcard permissions depend on concrete package and warning analysis stays conservative`() {
        val data = yaml("""
            rules:
              com.app: [com.shared]
              com.app.foo: {inherit: false, allow: [com.lib]}
              com.app.*.api: [com.extra]
            classAllowlist:
              com.app.foo.api.Caller: [com.shared.Shared]
        """).parseYamlImportRules()!!
        val rule = TrackedPackage("com.app.*.api")
        repeat(2) {
            assertThat(data.permissions.forPackage(rule, "com.app.foo.api"))
                .containsExactly(TrackedPackage("com.lib"), TrackedPackage("com.extra"))
            assertThat(data.permissions.forPackage(rule, "com.app.bar.api"))
                .containsExactly(TrackedPackage("com.shared"), TrackedPackage("com.extra"))
        }
        assertThat(data.warnings).isEmpty()
    }

    @Test
    fun `class rule warnings account for inherited permissions`() {
        val data = yaml(
            """
            rules:
              com.app.runtime: [com.app.commons]
              com.app.runtime.base:
                inherit: true
                allow: []
            classAllowlist:
              com.app.runtime.base.Foo: [com.app.commons.Bar]
            """
        ).parseYamlImportRules()!!
        assertThat(data.warnings).hasSize(1)
        assertThat(data.warnings.single()).contains("no effect")
    }

    @Test
    fun `equivalent parent spellings merge and unrelated siblings do not inherit`() {
        val data = yaml(
            """
            rules:
              com.app.runtime: [com.app.commons]
              com.app.runtime.**: [com.app.util]
              com.app.runtime.base: []
              com.app.runtimeExtra: []
            """
        ).parseYamlImportRules()!!
        assertThat(data.rules[TrackedPackage("com.app.runtime.base")])
            .containsExactly(TrackedPackage("com.app.commons"), TrackedPackage("com.app.util"))
        assertThat(data.rules[TrackedPackage("com.app.runtimeExtra")]).isEmpty()
    }

    @Test
    fun `permissions accumulate independent of declaration order and stop at reset`() {
        val data = yaml(
            """
            rules:
              com.app.runtime.base.deep: []
              com.app.runtime.base: [com.app.util]
              com.app.runtime.isolated.child: []
              com.app.runtime.isolated:
                inherit: false
                allow: [com.app.commons]
              com.app.runtime: [com.app.commons, com.app.legacy]
            """
        ).parseYamlImportRules()!!
        fun permissions(path: String) = data.rules.getValue(TrackedPackage(path)).map { it.pattern }
        assertThat(permissions("com.app.runtime.base.deep"))
            .containsExactly("com.app.commons", "com.app.legacy", "com.app.util")
        assertThat(permissions("com.app.runtime.isolated.child")).containsExactly("com.app.commons")
    }

    @Test
    fun `arrow rules and dependency-only packages inherit`() {
        val data = yaml(
            """
            rules:
              - com.app.runtime <- com.app.commons
              - com.app.consumer <- com.app.runtime.base
            """
        ).parseYamlImportRules()!!
        assertThat(data.rules[TrackedPackage("com.app.runtime.base")])
            .containsExactly(TrackedPackage("com.app.commons"))
    }

    @Test
    fun `object form supports composite keys and defaults to inheritance`() {
        val data = yaml(
            """
            rules:
              com.app.runtime: [com.app.commons]
              ? [com.app.runtime.base, com.app.runtime.other]
              :
                allow: [com.app.util]
              com.app.runtime.empty:
                inherit: false
                allow: []
            """
        ).parseYamlImportRules()!!
        for (child in listOf("base", "other")) {
            assertThat(data.rules[TrackedPackage("com.app.runtime.$child")])
                .containsExactly(TrackedPackage("com.app.commons"), TrackedPackage("com.app.util"))
        }
        assertThat(data.rules[TrackedPackage("com.app.runtime.empty")]).isEmpty()
    }

    @Test
    fun `exact and wildcard children inherit only enclosing literal subtrees`() {
        val data = yaml(
            """
            rules:
              com.app.runtime.**: [com.app.commons]
              com.app.runtime!: []
              com.app.runtime.*: []
              com.app.runtime.base: []
              com.app.*: [com.app.unrelated]
              com.app.runtime.base!: [com.app.exactOnly]
              com.app.runtime.base.deep: []
            """
        ).parseYamlImportRules()!!
        for (path in listOf("com.app.runtime!", "com.app.runtime.*", "com.app.runtime.base", "com.app.runtime.base.deep")) {
            val concrete = path.removeSuffix("!").replace("*", "feature")
            assertThat(data.permissions.forPackage(TrackedPackage(path), concrete))
                .containsExactly(TrackedPackage("com.app.commons"))
        }
    }

    @Test
    fun `invalid object options fail with actionable errors`() {
        for ((value, message) in listOf(
            "{allow: [], inherit: 'false'}" to "expected true or false",
            "{allow: [], inherit: null}" to "expected true or false",
            "{allow: [], inheritt: false}" to "Unknown rule option",
            "{inherit: false}" to "Invalid allow",
            "{allow: com.app.commons}" to "Invalid allow",
            "{allow: [42]}" to "expected a package string",
        )) {
            val error = assertThrows<GradleException> {
                yaml("rules:\n  com.app.runtime: $value").parseYamlImportRules()
            }
            assertThat(error).hasMessageThat().contains(message)
        }
    }

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
    fun `map rule with slash comment is rejected with YAML guidance`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  dev.ionfusion.commons.resources:
                    - dev.ionfusion.commons.util
                  dev.ionfusion.commons._private.io: // Should not depend on anything
                """
            ).parseYamlImportRules()
        }

        assertThat(ex.message).contains("rules")
        assertThat(ex.message).contains("dev.ionfusion.commons._private.io")
        assertThat(ex.message).contains("[]")
        assertThat(ex.message).contains("#")
        assertThat(ex.message).contains("//")
    }

    @Test
    fun `invalid scalar map rule values fail even alongside valid rules`() {
        for (value in listOf("", "null", "domain", "true", "42")) {
            val ex = assertThrows<GradleException> {
                yaml(
                    """
                    rules:
                      data: [domain]
                      legacy: $value
                    """
                ).parseYamlImportRules()
            }
            assertThat(ex.message).contains("legacy")
            assertThat(ex.message).contains("list")
            assertThat(ex.message).contains("[]")
        }
    }

    @Test
    fun `non-string package entries are rejected instead of silently ignored`() {
        val invalidRules = listOf(
            "- data <- domain\n- 42",
            "- data <- domain\n- null",
            "- data <- domain\n- { legacy: [] }",
            "data: [domain, 42]",
            "data: [domain, null]",
            "data: [domain, [legacy]]",
            "data: [domain]\n42: []",
            "data: [domain]\nnull: []",
            "data: [domain]\n? [ui, null]\n: []",
            "data: [domain]\n? [ui, 42]\n: []",
            "data: [domain]\n? []\n: []",
        )
        for (rules in invalidRules) {
            val ex = assertThrows<GradleException> {
                yaml("rules:\n" + rules.prependIndent("  ")).parseYamlImportRules()
            }
            assertThat(ex.message).contains("rules")
            assertThat(ex.message).contains("string")
        }
    }

    @Test
    fun `composite map keys and empty lists preserve tracking and permissions`() {
        val data = yaml(
            """
            rules:
              ? [ui, data]
              : [domain]
              ? [local, remote]
              : [] # No allowed dependencies
              legacy: [] # Tracked without any allowed dependencies
            """
        ).parseYamlImportRules()!!

        assertThat(data.checkedPackages.map { it.pattern })
            .containsExactly("ui", "data", "domain", "local", "remote", "legacy")
        for (importer in listOf("ui", "data")) {
            assertThat(data.rules[TrackedPackage(importer)])
                .containsExactly(TrackedPackage("domain"))
        }
        for (importer in listOf("domain", "local", "remote", "legacy")) {
            assertThat(data.rules[TrackedPackage(importer)]).isEmpty()
        }
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
                {}
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("rules or classAllowlist")
    }

    @Test
    fun `miscased class allowlist is rejected even alongside valid rules`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - com.example.api
                  - com.example.impl
                classAllowList:
                  - "com.example.api.** <- com.example.impl.FusionException"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).isEqualTo(
            "Unknown configuration key `classAllowList`. Did you mean `classAllowlist`?"
        )
    }

    @Test
    fun `miscased rules suggests the canonical key before checking for missing rules`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                Rules:
                  - data <- domain
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).isEqualTo(
            "Unknown configuration key `Rules`. Did you mean `rules`?"
        )
    }

    @Test
    fun `unknown top-level key is rejected even alongside valid rules`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - data <- domain
                unexpected: true
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).isEqualTo(
            "Unknown configuration key `unexpected`. Supported keys are `rules` and `classAllowlist`."
        )
    }

    @Test
    fun `non-string top-level key is rejected with a configuration error`() {
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - data <- domain
                123: true
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).isEqualTo(
            "Unknown configuration key `123`. Supported keys are `rules` and `classAllowlist`."
        )
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
    fun `right-arrow class rule routes importer and imported correctly through validation`() {
        // `->` swaps left/right at parse time; the error message must still
        // refer to the side as "importer" or "imported" by semantic role.
        val ex = assertThrows<GradleException> {
            yaml(
                """
                rules:
                  - com.example.api
                classAllowlist:
                  - "com.example.impl.Y -> com.example.api.X"
                """
            ).parseYamlImportRules()
        }
        assertThat(ex.message).contains("imported package")
        assertThat(ex.message).contains("com.example.impl")
    }

    @Test
    fun `class portion does not change package-coverage validation`() {
        // Class-name globs and explicit class names attach to the package
        // portion the same way; coverage validation looks only at the package.
        val data = yaml(
            """
            rules:
              - com.example.api
              - com.example.impl
            classAllowlist:
              - "com.example.api.** <- com.example.impl._Private_*"
            """
        ).parseYamlImportRules()!!

        assertThat(data.classRules).hasSize(1)
        assertThat(data.warnings).isEmpty()
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
