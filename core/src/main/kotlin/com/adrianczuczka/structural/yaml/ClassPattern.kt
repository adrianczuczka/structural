package com.adrianczuczka.structural.yaml

import org.gradle.api.GradleException

/**
 * A class-name pattern with shell-style wildcards on a single identifier.
 *
 * Supported forms:
 * - `Foo` — exact match (case-sensitive).
 * - `*Foo` — any class name ending with `Foo`.
 * - `Foo*` — any class name starting with `Foo`.
 * - `*Foo*` — any class name containing `Foo`.
 * - `*` — any class name.
 *
 * `**` is rejected (reserved for package paths). Class names cannot contain
 * `.`; nested-class patterns are not supported in this iteration.
 */
internal data class ClassPattern(val pattern: String) {
    private val regex: Regex by lazy { compileClassGlob(pattern) }

    fun matches(className: String): Boolean = regex.matches(className)

    override fun toString(): String = pattern
}

internal fun parseClassPattern(raw: String): ClassPattern {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        throw GradleException("Empty class pattern")
    }
    if (trimmed.contains('.')) {
        throw GradleException(
            "Invalid class pattern '$raw': class names cannot contain '.'"
        )
    }

    val starCount = trimmed.count { it == '*' }
    val shapeOk = when (starCount) {
        0 -> true
        1 -> trimmed.startsWith('*') || trimmed.endsWith('*')
        2 -> trimmed.startsWith('*') && trimmed.endsWith('*') && trimmed.length > 2
        else -> false
    }
    if (!shapeOk) {
        throw GradleException(
            "Invalid class pattern '$raw': '*' must appear only at the start or end (e.g. 'Foo', '*Foo', 'Foo*', '*Foo*', '*')"
        )
    }

    return ClassPattern(trimmed)
}

internal fun compileClassGlob(pattern: String): Regex {
    val sb = StringBuilder("^")
    pattern.forEach { ch ->
        if (ch == '*') sb.append("[^.]*") else sb.append(Regex.escape(ch.toString()))
    }
    sb.append("$")
    return Regex(sb.toString())
}
