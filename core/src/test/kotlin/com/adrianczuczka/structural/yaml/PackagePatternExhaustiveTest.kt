package com.adrianczuczka.structural.yaml

import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

class PackagePatternExhaustiveTest {

    @Test
    fun `matching and intersection agree with exhaustive short-pattern languages`() {
        val patterns = sequences(listOf("a", "b", "*", "**"), 3).map { it.joinToString(".") } +
            sequences(listOf("a", "b"), 3).filter { it.size > 1 }.map { it.joinToString(".") + "!" }

        // a and b cover every literal; x represents every other segment. A
        // shortest intersection witness needs at most the combined number of
        // non-** tokens (six here): consuming only ** on both sides cannot help
        // advance either pattern. Include nonempty packages only.
        val packages = sequences(listOf("a", "b", "x"), 6)
        val compiled = patterns.associateWith(::compilePackagePattern)
        val languages = patterns.associateWith { pattern ->
            packages.filter { referenceMatches(pattern, it) }.map { it.joinToString(".") }.toSet()
        }

        for (pattern in patterns) {
            for (parts in packages) {
                val pkg = parts.joinToString(".")
                assertWithMessage("%s matching %s", pattern, pkg)
                    .that(compiled.getValue(pattern).matches(pkg))
                    .isEqualTo(pkg in languages.getValue(pattern))
            }
            for (other in patterns) {
                val commonPackageExists = languages.getValue(pattern).any { it in languages.getValue(other) }
                assertWithMessage("%s intersecting %s", pattern, other)
                    .that(compiled.getValue(pattern).overlaps(compiled.getValue(other)))
                    .isEqualTo(commonPackageExists)
            }
        }
    }

    private fun sequences(alphabet: List<String>, maxLength: Int): List<List<String>> {
        var level = listOf(emptyList<String>())
        return buildList {
            repeat(maxLength) {
                level = level.flatMap { prefix -> alphabet.map { prefix + it } }
                addAll(level)
            }
        }
    }

    // Deliberately simple reference: try every possible length for each **.
    // This is independent of the production automata and is only used for
    // small, bounded inputs. Bare dotted paths allow any suffix.
    private fun referenceMatches(pattern: String, parts: List<String>): Boolean {
        val tokens = pattern.removeSuffix("!").split(".")
        val descendants = !pattern.endsWith("!") && tokens.size > 1 &&
            tokens.none { it == "*" || it == "**" }

        fun match(remainingTokens: List<String>, remainingParts: List<String>): Boolean {
            if (remainingTokens.isEmpty()) return descendants || remainingParts.isEmpty()
            return when (val token = remainingTokens.first()) {
                "**" -> (0..remainingParts.size).any { count ->
                    match(remainingTokens.drop(1), remainingParts.drop(count))
                }
                else -> remainingParts.isNotEmpty() && (token == "*" || token == remainingParts.first()) &&
                    match(remainingTokens.drop(1), remainingParts.drop(1))
            }
        }
        return match(tokens, parts)
    }
}
