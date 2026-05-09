package com.adrianczuczka.structural.yaml

import org.gradle.api.GradleException

internal data class TrackedPackage(val pattern: String) {
    private val regex: Regex by lazy { compilePackagePattern(pattern) }

    val isSingleSegment: Boolean =
        !pattern.contains('.') && !pattern.contains('!') && !pattern.contains('*')

    fun matches(pkg: String): Boolean = regex.matches(pkg)

    override fun toString(): String = pattern
}

/**
 * Parses a raw token from a rule side into a [TrackedPackage].
 *
 * Supported forms:
 * - bare multi-segment (e.g. `com.example`) — matches that path and any subpackage.
 * - trailing `.**` — same as bare, explicit form.
 * - `*` segment (e.g. `com.*.api`) — matches exactly one segment.
 * - `**` segment (e.g. `com.**.api`) — matches zero or more segments.
 * - trailing `!` (e.g. `com.example!`) — exact match, no wildcards allowed.
 * - single-segment literal (e.g. `data`) — legacy last-segment matching; no
 *   wildcards or `!` permitted on single-segment tokens.
 */
internal fun parseTrackedPackage(raw: String): TrackedPackage {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        throw GradleException("Empty package token")
    }

    val exact = trimmed.endsWith("!")
    val body = if (exact) trimmed.dropLast(1) else trimmed

    if (body.isEmpty()) {
        throw GradleException("Invalid package token '$raw': '!' must follow a package name")
    }
    if (exact && body.contains('*')) {
        throw GradleException("Invalid package token '$raw': '!' cannot be combined with wildcards")
    }

    val segments = body.split(".")
    val hasWildcard = segments.any { it == "*" || it == "**" }

    if (!body.contains('.') && (exact || hasWildcard)) {
        throw GradleException(
            "Invalid package token '$raw': wildcards and '!' require a fully-qualified (multi-segment) package"
        )
    }

    segments.forEach { seg ->
        if (seg.isEmpty()) {
            throw GradleException("Invalid package token '$raw': empty segment")
        }
        if (seg != "*" && seg != "**" && seg.contains('*')) {
            throw GradleException(
                "Invalid package token '$raw': segment '$seg' mixes literal text with '*'; use a whole-segment wildcard instead"
            )
        }
    }

    return TrackedPackage(if (exact) "$body!" else body)
}

internal fun compilePackagePattern(pattern: String): Regex {
    if (pattern.endsWith("!")) {
        val path = pattern.dropLast(1)
        return Regex("^${Regex.escape(path)}$")
    }

    val segments = pattern.split(".")
    val hasWildcards = segments.any { it == "*" || it == "**" }
    val sb = StringBuilder("^")

    segments.forEachIndexed { index, seg ->
        val isFirst = index == 0
        val isLast = index == segments.size - 1

        if (seg == "**") {
            when {
                isFirst && isLast -> sb.append("(?:[^.]+(?:\\.[^.]+)*)?")
                isFirst -> sb.append("(?:[^.]+\\.)*")
                else -> sb.append("(?:\\.[^.]+)*")
            }
            return@forEachIndexed
        }

        if (!isFirst) {
            val prev = segments[index - 1]
            val prevIsLeadingDoubleStar = prev == "**" && index == 1
            if (!prevIsLeadingDoubleStar) {
                sb.append("\\.")
            }
        }

        sb.append(if (seg == "*") "[^.]+" else Regex.escape(seg))
    }

    if (!hasWildcards && segments.size > 1) {
        sb.append("(?:\\.[^.]+)*")
    }

    sb.append("$")
    return Regex(sb.toString())
}

/**
 * True if there exists at least one concrete package that both [a] and [b]
 * would match at runtime.
 *
 * Two strategies, applied in order:
 * - If either side is a single-segment token (e.g. `data`), overlap reduces
 *   to "does the multi-segment side contain a segment that could match the
 *   single one?" — a literal `data`, `*`, or `**` segment.
 * - Otherwise both are multi-segment patterns. Generate a representative
 *   concrete package from each (replacing wildcards with placeholder
 *   segments) and check whether the other pattern's regex matches it. If
 *   either direction matches, the patterns overlap.
 *
 * The representative-package approach has known false negatives for unusual
 * patterns with mid-path `**` collisions, but covers all common shapes.
 */
internal fun overlap(a: TrackedPackage, b: TrackedPackage): Boolean {
    if (a == b) return true
    if (a.isSingleSegment) return overlapsSingleMulti(a, b)
    if (b.isSingleSegment) return overlapsSingleMulti(b, a)
    return overlapsMultiMulti(a, b)
}

private fun overlapsSingleMulti(single: TrackedPackage, multi: TrackedPackage): Boolean {
    val singleSeg = single.pattern
    val multiBody = if (multi.pattern.endsWith("!")) multi.pattern.dropLast(1) else multi.pattern
    return multiBody.split(".").any { it == singleSeg || it == "*" || it == "**" }
}

private fun overlapsMultiMulti(a: TrackedPackage, b: TrackedPackage): Boolean {
    val aCandidate = a.candidatePackage()
    val bCandidate = b.candidatePackage()
    return b.matches(aCandidate) || a.matches(bCandidate)
}

private fun TrackedPackage.candidatePackage(): String {
    val body = if (pattern.endsWith("!")) pattern.dropLast(1) else pattern
    return body.split(".").joinToString(".") { seg ->
        if (seg == "*" || seg == "**") "_x_" else seg
    }
}

/**
 * Rough specificity score — higher = more specific. Used to break ties when
 * multiple tracked packages match the same file/import (literal wins over
 * `*`, `*` over `**`, longer pattern over shorter).
 */
internal fun TrackedPackage.specificity(): Int {
    if (pattern.endsWith("!")) return Int.MAX_VALUE
    val segments = pattern.split(".")
    var score = 0
    segments.forEach { seg ->
        score += when (seg) {
            "**" -> 1
            "*" -> 100
            else -> 10_000 + seg.length
        }
    }
    return score
}
