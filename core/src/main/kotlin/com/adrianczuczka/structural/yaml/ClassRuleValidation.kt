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
    val multiSegmentTracked = tracked.filterNot { it.isSingleSegment }.sortedByDescending { it.specificity() }
    val hasSingleSegmentTracked = tracked.any { it.isSingleSegment }

    classRules.forEach { rule ->
        val importerCovered = tracked.any { rule.importer.packagePattern.matchPattern.overlaps(it.trackingPattern) }
        if (!importerCovered) {
            throw GradleException(
                "class rule ${rule.display()} references importer package " +
                    "`${rule.importer.packagePattern}` but no rule in `rules:` covers it. " +
                    "Declare the package under `rules:` (e.g. `- ${rule.importer.packagePattern}` " +
                    "for a tracked-but-isolated layer, or as part of an arrow rule)."
            )
        }

        val importedCovered = tracked.any { rule.imported.packagePattern.matchPattern.overlaps(it.trackingPattern) }
        if (!importedCovered) {
            throw GradleException(
                "class rule ${rule.display()} references imported package " +
                    "`${rule.imported.packagePattern}` but no rule in `rules:` covers it. " +
                    "Declare the package under `rules:` (e.g. `- ${rule.imported.packagePattern}` " +
                    "for a tracked-but-isolated layer, or as part of an arrow rule)."
            )
        }

        // Legacy single-segment checks depend on a package's ancestors and can
        // check several layers for one file. Only prove redundancy here when
        // that runtime path is unreachable, or no single-segment rules exist.
        if (hasSingleSegmentTracked && multiSegmentTracked.none { it.coversForWarning(rule.importer.packagePattern) }) {
            return@forEach
        }

        val importers = possibleTrackedPackages(rule.importer.packagePattern, multiSegmentTracked)
        val imported = possibleTrackedPackages(rule.imported.packagePattern, multiSegmentTracked)
        if (importers.isEmpty() || imported.isEmpty()) return@forEach

        // These sets may include extra possibilities, but must never miss a
        // runtime match. One potentially forbidden pair is enough to withhold
        // a warning: removing the class rule might change enforcement.
        if (importers.any { from -> imported.any { to -> from != to && to !in rules[from].orEmpty() } }) {
            return@forEach
        }

        val importerTracked = importers.singleOrNull()
        val importedTracked = imported.singleOrNull()

        if (importerTracked != null && importerTracked == importedTracked) {
            warnings += "class rule ${rule.display()} has no effect — both sides fall under " +
                "tracked package `$importerTracked`, so imports between them are auto-allowed."
            return@forEach
        }

        if (importerTracked != null && importedTracked != null) {
            warnings += "class rule ${rule.display()} has no effect — package rule already " +
                "permits `$importerTracked` to import from `$importedTracked`."
        } else {
            warnings += "class rule ${rule.display()} has no effect — package rules already " +
                "permit every tracked package combination matched by this rule."
        }
    }

    return ClassRuleValidation(warnings)
}

/**
 * An overestimate of the multi-segment packages runtime matching can select.
 * Intersections are exact, but containment is conservative. Preserve runtime
 * ordering and drop a candidate only when an earlier match is proven to cover it.
 */
private fun possibleTrackedPackages(
    pattern: TrackedPackage,
    byMostSpecific: List<TrackedPackage>,
): List<TrackedPackage> {
    val possible = mutableListOf<TrackedPackage>()
    for (tracked in byMostSpecific) {
        if (!pattern.matchPattern.overlaps(tracked.matchPattern)) continue
        if (possible.none { it.coversForWarning(tracked) }) possible += tracked
        if (tracked.coversForWarning(pattern)) break
    }
    return possible
}

/** True only for containment we can prove; false also means unknown. */
private fun TrackedPackage.coversForWarning(other: TrackedPackage): Boolean {
    if (this == other) return true
    if (other.pattern.endsWith("!") || other.isSingleSegment) {
        return matches(other.pattern.removeSuffix("!"))
    }
    if (pattern.endsWith("!")) return false

    // A literal hierarchy (bare or ending in .**) covers any pattern with
    // that fixed prefix. More complex wildcard containment is left unknown.
    val hierarchy = pattern.removeSuffix(".**")
    if ('*' in hierarchy || isSingleSegment) return false
    val prefix = hierarchy.split(".")
    return other.literalPrefix().take(prefix.size) == prefix
}

private fun TrackedPackage.literalPrefix(): List<String> =
    pattern.removeSuffix("!").split(".").takeWhile { it != "*" && it != "**" }

private fun ClassRule.display(): String =
    "`${importer.display()} <- ${imported.display()}`"

private fun ClassRuleToken.display(): String {
    val cls = classPattern?.pattern
    return if (cls != null) "${packagePattern.pattern}.$cls" else packagePattern.pattern
}
