package com.adrianczuczka.structural.yaml

import org.gradle.api.GradleException

/**
 * One side of a class-level import rule. Carries a package pattern (which side
 * of the rule the source/import belongs to) and an optional class-name pattern
 * that further narrows the match.
 */
internal data class ClassRuleToken(
    val packagePattern: TrackedPackage,
    val classPattern: ClassPattern?,
)

/**
 * A single class-level import rule: `importer <- imported`.
 */
internal data class ClassRule(
    val importer: ClassRuleToken,
    val imported: ClassRuleToken,
)

/**
 * Parses a class-rule token using the disambiguation rule:
 * - A segment containing `*` (other than the whole-segment `*`/`**` package
 *   wildcards) is a class-name glob; the class region starts there.
 * - Else, the first segment whose first non-underscore character is uppercase
 *   starts the class region.
 * - Else, all-lowercase token = pure package, no class part.
 * - Trailing `:name` forces `name` to be the class regardless of casing.
 *
 * Nested-class patterns (multiple class-name segments) are rejected.
 * Class-rule tokens require a package prefix.
 */
internal fun parseClassRuleToken(raw: String): ClassRuleToken {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        throw GradleException("Empty class-rule token")
    }

    val segments = trimmed.split(".")
    if (segments.any { it.isEmpty() }) {
        throw GradleException("Invalid class-rule token '$raw': empty segment")
    }

    val lastSegment = segments.last()
    if (lastSegment.startsWith(":")) {
        return parseEscapedToken(raw, segments, lastSegment)
    }

    val classStart = segments.indexOfFirst { isClassSegment(it) }
    if (classStart == -1) {
        return ClassRuleToken(
            packagePattern = parseTrackedPackage(trimmed),
            classPattern = null,
        )
    }

    val classSegments = segments.drop(classStart)
    if (classSegments.size > 1) {
        throw GradleException(
            "Invalid class-rule token '$raw': nested-class patterns are not supported in this version (class rules cover top-level class names only)"
        )
    }

    val packageSegments = segments.take(classStart)
    if (packageSegments.isEmpty()) {
        throw GradleException(
            "Invalid class-rule token '$raw': class-rule tokens require a package prefix (e.g. 'com.example.Foo')"
        )
    }

    return ClassRuleToken(
        packagePattern = parseTrackedPackage(packageSegments.joinToString(".")),
        classPattern = parseClassPattern(classSegments.single()),
    )
}

private fun parseEscapedToken(
    raw: String,
    segments: List<String>,
    lastSegment: String,
): ClassRuleToken {
    val classToken = lastSegment.removePrefix(":")
    if (classToken.isEmpty()) {
        throw GradleException(
            "Invalid class-rule token '$raw': ':' must be followed by a class name"
        )
    }
    if (segments.size == 1) {
        throw GradleException(
            "Invalid class-rule token '$raw': ':' escape requires a package prefix"
        )
    }
    val packageSegments = segments.dropLast(1)
    return ClassRuleToken(
        packagePattern = parseTrackedPackage(packageSegments.joinToString(".")),
        classPattern = parseClassPattern(classToken),
    )
}

private fun isClassSegment(seg: String): Boolean {
    if (seg == "*" || seg == "**") return false
    if (seg.contains('*')) return true
    val firstNonUnderscore = seg.firstOrNull { it != '_' } ?: return false
    return firstNonUnderscore.isUpperCase()
}

/**
 * True if the given file (its package + simple-name) and import (its imported
 * package + class name) is granted by this class rule.
 *
 * `null` class patterns mean "any class name" — they trivially match.
 * Imports without a class name (wildcard imports) cannot be granted by class
 * rules and the caller is expected to short-circuit before calling this.
 */
internal fun ClassRule.matches(
    importerPackage: String,
    importerClassName: String,
    importedPackage: String,
    importedClassName: String,
): Boolean {
    if (!importer.packagePattern.matches(importerPackage)) return false
    if (importer.classPattern?.matches(importerClassName) == false) return false
    if (!imported.packagePattern.matches(importedPackage)) return false
    if (imported.classPattern?.matches(importedClassName) == false) return false
    return true
}
