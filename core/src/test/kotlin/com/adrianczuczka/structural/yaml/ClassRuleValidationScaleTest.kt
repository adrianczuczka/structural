package com.adrianczuczka.structural.yaml

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ClassRuleValidationScaleTest {
    @Test
    fun `hundreds of broad class rules check every package pair and preserve individual warnings`() {
        val importers = (0 until 250).map { parseTrackedPackage("com.app.importers.p$it") }
        val targets = (0 until 250).map { parseTrackedPackage("com.app.targets.p$it") }
        val rules = importers.associateWith { targets } + targets.associateWith { emptyList() }
        val classes = (0 until 250).map {
            ClassRule(
                parseClassRuleToken("com.app.importers.**.Client$it"),
                parseClassRuleToken("com.app.targets.**.Internal$it"),
            )
        }

        val warnings = validateClassRules(classes, rules).warnings
        assertThat(warnings).hasSize(classes.size)
        warnings.forEachIndexed { index, warning ->
            assertThat(warning).contains("Client$index <- com.app.targets.**.Internal$index`")
        }

        // A denial at the end of the candidate pairs makes every class rule
        // necessary. Results from the previous config must not be reused.
        val denyingRules = rules + (importers.last() to targets.dropLast(1))
        assertThat(validateClassRules(classes, denyingRules).warnings).isEmpty()
    }
}
