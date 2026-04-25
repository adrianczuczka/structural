package com.adrianczuczka.structural.yaml

import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClassPatternTest {

    private fun matches(pattern: String, vararg names: String): List<Boolean> =
        names.map { parseClassPattern(pattern).matches(it) }

    @Test
    fun `exact name matches only itself`() {
        assertThat(matches("Foo", "Foo", "Foox", "xFoo", "foo"))
            .containsExactly(true, false, false, false)
            .inOrder()
    }

    @Test
    fun `prefix wildcard matches any name ending with the literal`() {
        assertThat(matches("*Foo", "Foo", "MyFoo", "FooBar", "Bar"))
            .containsExactly(true, true, false, false)
            .inOrder()
    }

    @Test
    fun `suffix wildcard matches any name starting with the literal`() {
        assertThat(matches("Foo*", "Foo", "FooBar", "MyFoo"))
            .containsExactly(true, true, false)
            .inOrder()
    }

    @Test
    fun `surrounding wildcards match any name containing the literal`() {
        assertThat(matches("*Foo*", "Foo", "MyFoo", "FooBar", "MyFooBar", "Bar"))
            .containsExactly(true, true, true, true, false)
            .inOrder()
    }

    @Test
    fun `lone star matches any non-empty identifier`() {
        assertThat(matches("*", "Foo", "Bar", "x"))
            .containsExactly(true, true, true)
            .inOrder()
    }

    @Test
    fun `class glob is case sensitive`() {
        assertThat(matches("Foo*", "Foo", "FOOBAR", "fooBar"))
            .containsExactly(true, false, false)
            .inOrder()
    }

    @Test
    fun `double star is rejected`() {
        val ex = assertThrows<GradleException> { parseClassPattern("**") }
        assertThat(ex.message).contains("**")
    }

    @Test
    fun `mid-pattern star is rejected`() {
        assertThrows<GradleException> { parseClassPattern("Foo*Bar") }
    }

    @Test
    fun `triple star is rejected`() {
        assertThrows<GradleException> { parseClassPattern("***") }
    }

    @Test
    fun `dot in class pattern is rejected`() {
        val ex = assertThrows<GradleException> { parseClassPattern("Foo.Bar") }
        assertThat(ex.message).contains("'.'")
    }

    @Test
    fun `empty pattern is rejected`() {
        assertThrows<GradleException> { parseClassPattern("   ") }
    }

    @Test
    fun `whitespace is trimmed`() {
        assertThat(parseClassPattern("  Foo  ").pattern).isEqualTo("Foo")
    }

    @Test
    fun `underscore-prefixed names parse cleanly`() {
        assertThat(matches("_Private_*", "_Private_", "_Private_Helper", "Private_Helper"))
            .containsExactly(true, true, false)
            .inOrder()
    }
}
