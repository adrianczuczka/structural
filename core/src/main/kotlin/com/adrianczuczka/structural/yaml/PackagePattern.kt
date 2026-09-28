package com.adrianczuczka.structural.yaml

import org.gradle.api.GradleException

internal data class TrackedPackage(val pattern: String) {
    val matchPattern: PackagePattern by lazy { compilePackagePattern(pattern) }

    val isSingleSegment: Boolean =
        !pattern.contains('.') && !pattern.contains('!') && !pattern.contains('*')

    // Legacy tracked names match a segment at any depth. Class-rule package
    // prefixes use matchPattern instead: api.Client belongs only to package api.
    // This describes membership, not runtime precedence or ancestor permissions.
    val trackingPattern: PackagePattern by lazy {
        if (isSingleSegment) compilePackagePattern("**.$pattern.**") else matchPattern
    }

    fun matches(pkg: String): Boolean = matchPattern.matches(pkg)

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
 * - single-segment literal (e.g. `data`) — ancestor matching in tracked rules,
 *   literal matching in class-rule prefixes; no wildcards or `!` permitted.
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

internal fun compilePackagePattern(pattern: String): PackagePattern {
    val segments = pattern.removeSuffix("!").split(".")
    val implicitDescendants = !pattern.endsWith("!") && segments.size > 1 &&
        segments.none { it == "*" || it == "**" }
    return PackagePattern(if (implicitDescendants) segments + "**" else segments)
}

/**
 * A segment automaton shared by concrete matching and intersection checks.
 * A literal or `*` consumes one segment and advances; `**` can advance without
 * consuming, or consume any segment and stay at the same position.
 */
internal class PackagePattern(private val segments: List<String>) {
    /** O(m * n) time and O(m + n) working space, counting pattern/package segments. */
    fun matches(pkg: String): Boolean {
        val parts = pkg.split(".")
        if (parts.any { it.isEmpty() }) return false

        var current = BooleanArray(segments.size + 1)
        var next = BooleanArray(segments.size + 1)
        current[0] = true
        skipDoubleStars(current)
        for (part in parts) {
            next.fill(false)
            for (i in segments.indices) {
                if (!current[i]) continue
                when (segments[i]) {
                    "**" -> next[i] = true
                    "*", part -> next[i + 1] = true
                }
            }
            skipDoubleStars(next)
            val previous = current
            current = next
            next = previous
        }
        return current.last()
    }

    private fun skipDoubleStars(states: BooleanArray) {
        for (i in segments.indices) {
            if (states[i] && segments[i] == "**") states[i + 1] = true
        }
    }

    /**
     * Whether a nonempty package matches both patterns. Explore the product of
     * their automata, visiting each position pair at most twice (before/after
     * consuming a segment). O(m * n) time and space for pattern lengths m, n.
     * This says nothing about containment or which tracked rule wins at runtime.
     */
    fun overlaps(other: PackagePattern): Boolean {
        val pending = ArrayDeque<IntersectionState>()
        val visited = mutableSetOf<IntersectionState>()
        fun enqueue(left: Int, right: Int, consumed: Boolean) {
            val state = IntersectionState(left, right, consumed)
            if (visited.add(state)) pending.addLast(state)
        }

        enqueue(0, 0, false)
        while (pending.isNotEmpty()) {
            val (i, j, consumed) = pending.removeFirst()
            if (i == segments.size && j == other.segments.size && consumed) return true
            val left = segments.getOrNull(i)
            val right = other.segments.getOrNull(j)
            if (left == "**") enqueue(i + 1, j, consumed)
            if (right == "**") enqueue(i, j + 1, consumed)
            if (left == null || right == null) continue

            if (left == right || left == "*" || left == "**" || right == "*" || right == "**") {
                enqueue(if (left == "**") i else i + 1, if (right == "**") j else j + 1, true)
            }
        }
        return false
    }

    private data class IntersectionState(val left: Int, val right: Int, val consumed: Boolean)
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
