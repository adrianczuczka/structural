package com.adrianczuczka.structural.yaml

import org.gradle.api.GradleException

/**
 * Result of validating class rules against the tracked-package set and the
 * package-level rules. Errors abort the build; warnings are surfaced at task
 * execution time so users discover dead rules without breaking them.
 */
internal data class ClassRuleValidation(val warnings: List<String>)

internal fun validateClassRules(
    classRules: List<ClassRule>,
    rules: Map<TrackedPackage, List<TrackedPackage>>,
): ClassRuleValidation {
    val tracked = rules.keys
    val warnings = mutableListOf<String>()

    classRules.forEach { rule ->
        val importerCovered = tracked.any { overlap(rule.importer.packagePattern, it) }
        if (!importerCovered) {
            throw GradleException(
                "class rule ${rule.display()} references importer package " +
                    "`${rule.importer.packagePattern}` but no rule in `rules:` covers it. " +
                    "Declare the package under `rules:` (e.g. `- ${rule.importer.packagePattern}` " +
                    "for a tracked-but-isolated layer, or as part of an arrow rule)."
            )
        }

        val importedCovered = tracked.any { overlap(rule.imported.packagePattern, it) }
        if (!importedCovered) {
            throw GradleException(
                "class rule ${rule.display()} references imported package " +
                    "`${rule.imported.packagePattern}` but no rule in `rules:` covers it. " +
                    "Declare the package under `rules:` (e.g. `- ${rule.imported.packagePattern}` " +
                    "for a tracked-but-isolated layer, or as part of an arrow rule)."
            )
        }

        // Case 4: importer and imported overlap with the same tracked package
        // → at runtime, the import is auto-allowed (same tracked hierarchy),
        // so the class rule never fires. Emit a warning.
        val sharedTracked = tracked.firstOrNull {
            overlap(rule.importer.packagePattern, it) && overlap(rule.imported.packagePattern, it)
        }
        if (sharedTracked != null) {
            warnings += "class rule ${rule.display()} has no effect — both sides fall under " +
                "tracked package `$sharedTracked`, so imports between them are auto-allowed."
            return@forEach
        }

        // Case 3: package rule already grants the cross-package import → class
        // rule is redundant. Emit a warning.
        val importerTracked = tracked.first { overlap(rule.importer.packagePattern, it) }
        val importedTracked = tracked.first { overlap(rule.imported.packagePattern, it) }
        if (importedTracked in (rules[importerTracked] ?: emptyList())) {
            warnings += "class rule ${rule.display()} has no effect — package rule already " +
                "permits `$importerTracked` to import from `$importedTracked`."
        }
    }

    return ClassRuleValidation(warnings)
}

private fun ClassRule.display(): String =
    "`${importer.display()} <- ${imported.display()}`"

private fun ClassRuleToken.display(): String {
    val cls = classPattern?.pattern
    return if (cls != null) "${packagePattern.pattern}.$cls" else packagePattern.pattern
}
