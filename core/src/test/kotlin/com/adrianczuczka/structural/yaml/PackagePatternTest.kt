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
}
