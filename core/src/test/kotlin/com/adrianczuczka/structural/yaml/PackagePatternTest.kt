package com.adrianczuczka.structural.yaml

import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PackagePatternTest {

    private fun matches(pattern: String, vararg pkgs: String): List<Boolean> =
        pkgs.map { compilePackagePattern(pattern).matches(it) }

    @Test
    fun `bare multi-segment matches path and subpackages`() {
        assertThat(matches("com.example", "com.example", "com.example.foo", "com.example.foo.bar", "com.other"))
            .containsExactly(true, true, true, false)
            .inOrder()
    }

    @Test
    fun `explicit trailing double-star matches same as bare`() {
        assertThat(matches("com.example.**", "com.example", "com.example.foo", "com.example.foo.bar", "com.other"))
            .containsExactly(true, true, true, false)
            .inOrder()
    }

    @Test
    fun `exact suffix matches only the literal path`() {
        assertThat(matches("com.example!", "com.example", "com.example.foo", "com.examplex"))
            .containsExactly(true, false, false)
            .inOrder()
    }

    @Test
    fun `single star matches exactly one segment`() {
        assertThat(matches("com.example.*", "com.example", "com.example.foo", "com.example.foo.bar"))
            .containsExactly(false, true, false)
            .inOrder()
    }

    @Test
    fun `middle star matches any single intermediate segment`() {
        assertThat(matches("com.*.api", "com.foo.api", "com.api", "com.foo.bar.api"))
            .containsExactly(true, false, false)
            .inOrder()
    }

    @Test
    fun `middle double-star matches zero or more segments`() {
        assertThat(matches("com.**.internal", "com.internal", "com.foo.internal", "com.foo.bar.internal", "com.external"))
            .containsExactly(true, true, true, false)
            .inOrder()
    }

    @Test
    fun `leading double-star matches any prefix`() {
        assertThat(matches("**.private", "private", "com.private", "com.foo.private", "com.privatex"))
            .containsExactly(true, true, true, false)
            .inOrder()
    }

    @Test
    fun `lone double-star matches anything`() {
        assertThat(matches("**", "a", "a.b", "a.b.c"))
            .containsExactly(true, true, true)
            .inOrder()
    }

    @Test
    fun `leading double-star followed by single-star matches one-or-more segments`() {
        assertThat(matches("**.*", "a", "a.b", "a.b.c"))
            .containsExactly(true, true, true)
            .inOrder()
    }

    @Test
    fun `parseTrackedPackage strips and records exact suffix`() {
        val parsed = parseTrackedPackage("com.example!")
        assertThat(parsed.pattern).isEqualTo("com.example!")
        assertThat(parsed.matches("com.example")).isTrue()
        assertThat(parsed.matches("com.example.foo")).isFalse()
    }

    @Test
    fun `parseTrackedPackage trims whitespace`() {
        val parsed = parseTrackedPackage("   com.example.**   ")
        assertThat(parsed.pattern).isEqualTo("com.example.**")
    }

    @Test
    fun `parseTrackedPackage rejects bang on single-segment`() {
        val ex = assertThrows<GradleException> { parseTrackedPackage("data!") }
        assertThat(ex.message).contains("data!")
    }

    @Test
    fun `parseTrackedPackage rejects wildcard on single-segment`() {
        assertThrows<GradleException> { parseTrackedPackage("data*") }
    }

    @Test
    fun `parseTrackedPackage rejects mixed token`() {
        val ex = assertThrows<GradleException> { parseTrackedPackage("com.foo*") }
        assertThat(ex.message).contains("foo*")
    }

    @Test
    fun `parseTrackedPackage rejects bang combined with wildcard`() {
        assertThrows<GradleException> { parseTrackedPackage("com.example.**!") }
    }

    @Test
    fun `parseTrackedPackage rejects empty segment`() {
        assertThrows<GradleException> { parseTrackedPackage("com..example") }
    }

    @Test
    fun `parseTrackedPackage rejects empty token`() {
        assertThrows<GradleException> { parseTrackedPackage("   ") }
    }

    @Test
    fun `specificity literal beats wildcard`() {
        val literal = parseTrackedPackage("com.example.foo").specificity()
        val singleStar = parseTrackedPackage("com.example.*").specificity()
        val doubleStar = parseTrackedPackage("com.example.**").specificity()
        assertThat(literal).isGreaterThan(singleStar)
        assertThat(singleStar).isGreaterThan(doubleStar)
    }

    private fun overlaps(a: String, b: String): Boolean =
        parseTrackedPackage(a).matchPattern.overlaps(parseTrackedPackage(b).matchPattern)

    private fun overlapsTracked(pattern: String, tracked: String): Boolean =
        parseTrackedPackage(pattern).matchPattern.overlaps(parseTrackedPackage(tracked).trackingPattern)

    @Test
    fun `overlap is reflexive`() {
        assertThat(overlaps("com.example.api", "com.example.api")).isTrue()
        assertThat(overlaps("data", "data")).isTrue()
        assertThat(overlaps("com.example.api!", "com.example.api!")).isTrue()
    }

    @Test
    fun `overlap of disjoint multi-segment paths is false`() {
        assertThat(overlaps("com.example.api", "com.example.impl")).isFalse()
        assertThat(overlaps("com.foo", "com.bar")).isFalse()
    }

    @Test
    fun `bare multi-segment overlaps with subpackage tracked`() {
        // tracked com.example.api covers files under com.example.api.**
        // class rule com.example.api.X (parsed as com.example.api) → overlap.
        assertThat(overlaps("com.example.api", "com.example.api")).isTrue()
    }

    @Test
    fun `trailing double-star overlaps with bare equivalent`() {
        assertThat(overlaps("com.example.api.**", "com.example.api")).isTrue()
    }

    @Test
    fun `exact bang overlaps with broader pattern that covers it`() {
        assertThat(overlaps("com.example.api!", "com.example.**")).isTrue()
        assertThat(overlaps("com.example.api!", "com.example.api")).isTrue()
    }

    @Test
    fun `exact bang does not overlap with disjoint exact bang`() {
        assertThat(overlaps("com.example.api!", "com.example.impl!")).isFalse()
    }

    @Test
    fun `tracked single-segment overlaps with multi-segment containing that segment`() {
        assertThat(overlapsTracked("com.example.api", "api")).isTrue()
        assertThat(overlapsTracked("com.example.api.**", "api")).isTrue()
        assertThat(overlapsTracked("com.example.api!", "api")).isTrue()
    }

    @Test
    fun `tracked single-segment overlaps with implicit descendants but not a disjoint exact package`() {
        // The bare path includes com.example.api.data.
        assertThat(overlapsTracked("com.example.api", "data")).isTrue()
        assertThat(overlapsTracked("com.example.impl!", "data")).isFalse()
    }

    @Test
    fun `single-segment overlaps with multi-segment containing double-star`() {
        // `**` can match any segment, including the single-segment value, so
        // `com.example.api.**` could match a file at `com.example.api.data.foo`,
        // which is also tracked by single-segment `data`.
        assertThat(overlapsTracked("com.example.api.**", "data")).isTrue()
        assertThat(overlapsTracked("com.**.internal", "anything")).isTrue()
    }

    @Test
    fun `single-segment overlaps with multi-segment containing single-star`() {
        assertThat(overlapsTracked("com.*.api", "foo")).isTrue()
    }

    @Test
    fun `disjoint single-segment patterns do not overlap`() {
        assertThat(overlaps("data", "domain")).isFalse()
    }

    @Test
    fun `single-segment class prefixes are literal even when tracked names are shorthand`() {
        assertThat(overlaps("api", "com.example.api")).isFalse()
        assertThat(overlapsTracked("api", "com.example.api")).isFalse()
        assertThat(overlapsTracked("api", "api")).isTrue()
        assertThat(overlapsTracked("api", "domain")).isFalse()
        assertThat(parseTrackedPackage("api").trackingPattern.matches("com.api.child")).isTrue()
        assertThat(parseTrackedPackage("api").matches("com.api.child")).isFalse()
    }

    @Test
    fun `mid-path double-star overlaps with shallower tracked match`() {
        // `com.**.api` should overlap with `com.api` because `**` can match
        // zero segments.
        assertThat(overlaps("com.**.api", "com.api")).isTrue()
    }

    @Test
    fun `mid-path double-star overlaps with deeper tracked match`() {
        assertThat(overlaps("com.**.api", "com.example.api")).isTrue()
    }

    @Test
    fun `single-star segment overlaps with literal that fits`() {
        assertThat(overlaps("com.*.api", "com.example.api")).isTrue()
    }

    @Test
    fun `wildcard pattern does not overlap with disjoint literal`() {
        assertThat(overlaps("com.*.api", "com.foo.bar")).isFalse()
    }

    @Test
    fun `overlap is symmetric across pattern shapes`() {
        val pairs = listOf(
            "com.example.api" to "com.example.api",
            "com.example.api" to "com.example.impl",
            "com.example.api.**" to "com.example.api",
            "com.example.api!" to "com.example.**",
            "com.*.api" to "com.example.api",
            "com.**.internal" to "com.internal",
            "data" to "com.example.data",
            "data" to "com.example.api.**",
            "data" to "domain",
        )
        pairs.forEach { (a, b) ->
            assertThat(overlaps(a, b)).isEqualTo(overlaps(b, a))
        }
    }

    @Test
    fun `leading double-star overlaps with single-segment that lives at the suffix`() {
        // `**.private` matches `private`, `foo.private`, `foo.bar.private`, etc.
        // A single-segment class prefix is literal, so these share `private`.
        assertThat(overlaps("**.private", "private")).isTrue()
        assertThat(overlaps("**.private", "com.example.private")).isTrue()
    }

    @Test
    fun `leading double-star overlaps with multi-segment ending in matching suffix`() {
        assertThat(overlaps("**.private", "com.example.private")).isTrue()
        // Bare com.example.api includes com.example.api.private.
        assertThat(overlaps("**.private", "com.example.api")).isTrue()
        assertThat(overlaps("**.private", "com.example.api!")).isFalse()
    }

    @Test
    fun `single-star intersections do not need a representative package from either pattern`() {
        assertThat(overlaps("com.*.api", "com.foo.*")).isTrue()
        assertThat(overlaps("com.*.api", "org.foo.*")).isFalse()
    }

    @Test
    fun `double-stars can consume different numbers of segments on either side`() {
        assertThat(overlaps("com.**.api.*", "com.*.**.impl")).isTrue()
        assertThat(overlaps("com.**.api", "com.api!")).isTrue()
        assertThat(overlaps("com.**.api", "com.**.impl")).isFalse()
        assertThat(overlaps("com.*.api", "com.api!")).isFalse()
    }

    @Test
    fun `consecutive double-stars match zero or more complete segments`() {
        assertThat(matches("**.**.api", "api", "com.api", "com.foo.api", "com.apix"))
            .containsExactly(true, true, true, false).inOrder()
        assertThat(matches("com.**.**.api", "com.api", "com.foo.api", "com.foo.bar.api"))
            .containsExactly(true, true, true).inOrder()
        assertThat(matches("**.**", "api", "com.api", "com.foo.api"))
            .containsExactly(true, true, true).inOrder()
        assertThat(overlaps("**.**.api", "com.api!")).isTrue()
    }

    @Test
    fun `package matching rejects empty segments`() {
        for (pattern in listOf("**", "**.**", "com.**", "**.api")) {
            assertThat(matches(pattern, "", ".api", "com.", "com..api"))
                .containsExactly(false, false, false, false)
        }
    }

    @Test
    fun `many recursive wildcards finish without recursive backtracking`() {
        val prefix = List(80) { "**.a" }.joinToString(".")
        val left = compilePackagePattern("$prefix.end")
        val right = compilePackagePattern("$prefix.other")
        assertThat(left.overlaps(right)).isFalse()
        assertThat(left.matches(List(160) { "a" }.joinToString(".") + ".other")).isFalse()
        assertThat(left.matches(List(160) { "a" }.joinToString(".") + ".end")).isTrue()
    }
}
