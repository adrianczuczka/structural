package com.adrianczuczka.structural.yaml

import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClassRuleValidationTest {

    @Test
    fun `broad dependency permission covers a separately tracked descendant`() {
        val warnings = warnings(
            "dev.ionfusion.fusion!.Caller", "dev.ionfusion.commons.resources.ResourcePosition",
            "dev.ionfusion.fusion!" to listOf("dev.ionfusion.commons.**"),
            "dev.ionfusion.commons.**" to emptyList(),
            "dev.ionfusion.commons.resources" to emptyList(),
        )
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single()).contains("package rule already permits")
    }

    @Test
    fun `dependency permission may cover a class rule without covering its entire tracked target`() {
        val warnings = warnings(
            "com.example.api!.Caller", "com.example.impl.public!.Helper",
            "com.example.api!" to listOf("com.example.*.public"),
            "com.example.*.public" to emptyList(),
            "com.example.impl" to emptyList(),
        )
        assertThat(warnings).hasSize(1)
    }

    @Test
    fun `overlapping dependency permission cannot prove an entire class rule redundant`() {
        assertThat(warnings(
            "com.example.api!.Caller", "com.example.impl.**.Helper",
            "com.example.api!" to listOf("com.example.impl.public!"),
            "com.example.impl.public!" to emptyList(),
            "com.example.impl" to emptyList(),
        )).isEmpty()
    }

    private fun warnings(
        importer: String,
        imported: String,
        vararg packageRules: Pair<String, List<String>>,
    ): List<String> = validateClassRules(
        listOf(ClassRule(parseClassRuleToken(importer), parseClassRuleToken(imported))),
        packageRules.associate { (from, to) -> parseTrackedPackage(from) to to.map(::parseTrackedPackage) },
    ).warnings

    @Test
    fun `broad importer can grant access outside an overlapping exact package`() {
        assertThat(warnings(
            "dev.ionfusion.**", "dev.ionfusion.fusion._Private_*",
            "dev.ionfusion.fusion!" to listOf("dev.ionfusion.runtime.embed"),
            "dev.ionfusion.runtime.embed" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `broad imported pattern can grant access outside an overlapping exact package`() {
        assertThat(warnings(
            "dev.ionfusion.fusion.Builder", "dev.ionfusion.**._Private_*",
            "dev.ionfusion.fusion!" to emptyList(),
            "dev.ionfusion.runtime.embed" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `package permission for only one importer does not make a broad rule redundant`() {
        assertThat(warnings(
            "com.example.api.**", "org.shared.Internal",
            "com.example.api.allowed" to listOf("org.shared"),
            "com.example.api.other" to emptyList(),
            "org.shared" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `package permission for only one imported package does not make a broad rule redundant`() {
        assertThat(warnings(
            "org.api.Client", "com.example.impl.**._Private_*",
            "org.api" to listOf("com.example.impl.allowed"),
            "com.example.impl.allowed" to emptyList(),
            "com.example.impl.other" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `broad rule is redundant when every possible pair is permitted`() {
        val warnings = warnings(
            "com.example.api.**", "com.example.impl.**",
            "com.example.api.one" to listOf("com.example.impl.one", "com.example.impl.two"),
            "com.example.api.two" to listOf("com.example.impl.one", "com.example.impl.two"),
            "com.example.impl.one" to emptyList(),
            "com.example.impl.two" to emptyList(),
        )
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single()).contains("permit every tracked package combination")
    }

    @Test
    fun `nested tracked package prevents treating the entire hierarchy as auto-allowed`() {
        assertThat(warnings(
            "com.example.app.**", "com.example.app.impl.Internal",
            "com.example.app" to emptyList(),
            "com.example.app.api" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `exact package rules take precedence over a broader denying rule`() {
        val warnings = warnings(
            "com.example.api!.Client", "com.example.impl!.Internal",
            "com.example" to emptyList(),
            "com.example.api!" to listOf("com.example.impl!"),
            "com.example.impl!" to emptyList(),
        )
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single()).contains("package rule already permits")
    }

    @Test
    fun `explicit recursive pattern wins over its bare equivalent at runtime`() {
        val warnings = warnings(
            "com.example.api.Client", "org.shared.Internal",
            "com.example.api" to emptyList(),
            "com.example.api.**" to listOf("org.shared"),
            "org.shared" to emptyList(),
        )
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single()).contains("`com.example.api.**` to import from `org.shared`")
    }

    @Test
    fun `equally specific patterns follow declaration order`() {
        val denying = "com.*.api" to emptyList<String>()
        val allowing = "com.foo.*" to listOf("org.shared")
        val target = "org.shared" to emptyList<String>()

        assertThat(warnings(
            "com.foo.api!.Client", "org.shared.Internal", denying, allowing, target,
        )).isEmpty()
        assertThat(warnings(
            "com.foo.api!.Client", "org.shared.Internal", allowing, denying, target,
        )).hasSize(1)
    }

    @Test
    fun `intersections missed by representative samples remain possible runtime matches`() {
        // com.foo.api matches both patterns. The first pattern wins the tie
        // and denies the import. Representative samples missed this intersection.
        assertThat(warnings(
            "com.*.api.Client", "org.shared.Internal",
            "com.foo.*" to emptyList(),
            "com.*.api" to listOf("org.shared"),
            "org.shared" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `disjoint wildcard suffixes do not suppress a redundancy warning`() {
        assertThat(warnings(
            "com.**.api.Client", "org.shared.Internal",
            "com.**.impl" to emptyList(),
            "com.**.api" to listOf("org.shared"),
            "org.shared" to emptyList(),
        )).hasSize(1)
    }

    @Test
    fun `crossed single-star patterns provide importer coverage`() {
        assertThat(warnings(
            "com.*.api.Client", "org.shared.Internal",
            "com.foo.*" to emptyList(),
            "org.shared" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `crossed single-star patterns provide imported coverage`() {
        assertThat(warnings(
            "org.shared.Client", "com.*.api.Internal",
            "com.foo.*" to emptyList(),
            "org.shared" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `implicit subpackages can overlap legacy tracked names`() {
        assertThat(warnings(
            "com.example.api.Client", "com.example.impl.Internal",
            "data" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `single-segment class prefixes cannot borrow coverage from nested packages`() {
        val importerError = assertThrows<GradleException> {
            warnings("api.Client", "org.shared.Internal",
                "com.api" to emptyList(), "org.shared" to emptyList())
        }
        assertThat(importerError.message).contains("importer package `api`")

        val importedError = assertThrows<GradleException> {
            warnings("org.shared.Client", "api.Internal",
                "com.api" to emptyList(), "org.shared" to emptyList())
        }
        assertThat(importedError.message).contains("imported package `api`")
    }

    @Test
    fun `legacy ancestor-dependent matching is not used to prove redundancy`() {
        assertThat(warnings(
            "com.api.Client", "com.impl.Internal",
            "api" to listOf("impl"),
            "impl" to emptyList(),
        )).isEmpty()
    }

    @Test
    fun `legacy rules do not suppress warnings when multi-segment coverage is complete`() {
        val warnings = warnings(
            "com.example.api.Client", "com.example.impl.Internal",
            "api" to emptyList(),
            "com.example.api" to listOf("com.example.impl"),
            "com.example.impl" to emptyList(),
        )
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single()).contains("package rule already permits")
    }

    @Test
    fun `an exact tracked package does not rule out legacy checks in its subpackages`() {
        assertThat(warnings(
            "com.api.Client", "com.impl.Internal",
            "com.api!" to listOf("com.impl"),
            "com.impl" to emptyList(),
            "api" to emptyList(),
        )).isEmpty()
    }
}
