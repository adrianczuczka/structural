package com.adrianczuczka.structural.yaml

import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ClassRuleTest {

    @Test
    fun `bare package wildcard yields null class part`() {
        val token = parseClassRuleToken("api.**")
        assertThat(token.packagePattern.pattern).isEqualTo("api.**")
        assertThat(token.classPattern).isNull()
    }

    @Test
    fun `uppercase trailing segment is the class name`() {
        val token = parseClassRuleToken("api.ApiBuilder")
        assertThat(token.packagePattern.pattern).isEqualTo("api")
        assertThat(token.classPattern?.pattern).isEqualTo("ApiBuilder")
    }

    @Test
    fun `star-bearing trailing segment is the class glob`() {
        val token = parseClassRuleToken("impl._Private_*")
        assertThat(token.packagePattern.pattern).isEqualTo("impl")
        assertThat(token.classPattern?.pattern).isEqualTo("_Private_*")
    }

    @Test
    fun `mid-path package wildcard followed by class glob`() {
        val token = parseClassRuleToken("impl.**._Private_*")
        assertThat(token.packagePattern.pattern).isEqualTo("impl.**")
        assertThat(token.classPattern?.pattern).isEqualTo("_Private_*")
    }

    @Test
    fun `multi-segment lowercase package with uppercase trailing class`() {
        val token = parseClassRuleToken("api.foo.Bar")
        assertThat(token.packagePattern.pattern).isEqualTo("api.foo")
        assertThat(token.classPattern?.pattern).isEqualTo("Bar")
    }

    @Test
    fun `all-lowercase token has no class part`() {
        val token = parseClassRuleToken("api.foo")
        assertThat(token.packagePattern.pattern).isEqualTo("api.foo")
        assertThat(token.classPattern).isNull()
    }

    @Test
    fun `underscore-prefixed uppercase identifier is recognised as class`() {
        val token = parseClassRuleToken("api._PrivateClass")
        assertThat(token.packagePattern.pattern).isEqualTo("api")
        assertThat(token.classPattern?.pattern).isEqualTo("_PrivateClass")
    }

    @Test
    fun `colon escape forces lowercase trailing token to be a class`() {
        val token = parseClassRuleToken("api.:listOf")
        assertThat(token.packagePattern.pattern).isEqualTo("api")
        assertThat(token.classPattern?.pattern).isEqualTo("listOf")
    }

    @Test
    fun `colon escape with multi-segment package`() {
        val token = parseClassRuleToken("com.example.api.:listOf")
        assertThat(token.packagePattern.pattern).isEqualTo("com.example.api")
        assertThat(token.classPattern?.pattern).isEqualTo("listOf")
    }

    @Test
    fun `nested-class pattern is rejected`() {
        val ex = assertThrows<GradleException> { parseClassRuleToken("api.Foo.Bar") }
        assertThat(ex.message).contains("nested-class")
    }

    @Test
    fun `class-only token without package is rejected`() {
        val ex = assertThrows<GradleException> { parseClassRuleToken("Foo") }
        assertThat(ex.message).contains("package prefix")
    }

    @Test
    fun `colon escape without package is rejected`() {
        assertThrows<GradleException> { parseClassRuleToken(":listOf") }
    }

    @Test
    fun `colon followed by nothing is rejected`() {
        assertThrows<GradleException> { parseClassRuleToken("api.:") }
    }

    @Test
    fun `empty token is rejected`() {
        assertThrows<GradleException> { parseClassRuleToken("   ") }
    }

    @Test
    fun `empty segment is rejected`() {
        assertThrows<GradleException> { parseClassRuleToken("api..Foo") }
    }

    @Test
    fun `whitespace is trimmed`() {
        val token = parseClassRuleToken("  api.ApiBuilder  ")
        assertThat(token.packagePattern.pattern).isEqualTo("api")
        assertThat(token.classPattern?.pattern).isEqualTo("ApiBuilder")
    }

    @Test
    fun `class rule matches all four sides`() {
        val rule = ClassRule(
            importer = parseClassRuleToken("api.**"),
            imported = parseClassRuleToken("impl.FusionException"),
        )
        assertThat(
            rule.matches(
                importerPackage = "com.example.api.deep",
                importerClassName = "Anything",
                importedPackage = "com.example.impl",
                importedClassName = "FusionException",
            )
        ).isFalse() // importer package is "com.example.api.deep" — doesn't match "api.**" because "api.**" expects path starting with "api"
    }

    @Test
    fun `class rule with package-rooted importer matches subpackage`() {
        val rule = ClassRule(
            importer = parseClassRuleToken("com.example.api.**"),
            imported = parseClassRuleToken("com.example.impl.FusionException"),
        )
        assertThat(
            rule.matches(
                importerPackage = "com.example.api.deep",
                importerClassName = "AnyClass",
                importedPackage = "com.example.impl",
                importedClassName = "FusionException",
            )
        ).isTrue()
    }

    @Test
    fun `class rule importer-class restriction limits matches`() {
        val rule = ClassRule(
            importer = parseClassRuleToken("com.example.api.ApiBuilder"),
            imported = parseClassRuleToken("com.example.impl.**"),
        )
        assertThat(
            rule.matches(
                importerPackage = "com.example.api",
                importerClassName = "ApiBuilder",
                importedPackage = "com.example.impl",
                importedClassName = "Anything",
            )
        ).isTrue()
        assertThat(
            rule.matches(
                importerPackage = "com.example.api",
                importerClassName = "OtherClass",
                importedPackage = "com.example.impl",
                importedClassName = "Anything",
            )
        ).isFalse()
    }

    @Test
    fun `class-name glob applies to imported side`() {
        val rule = ClassRule(
            importer = parseClassRuleToken("com.example.api.**"),
            imported = parseClassRuleToken("com.example.impl._Private_*"),
        )
        assertThat(
            rule.matches(
                importerPackage = "com.example.api",
                importerClassName = "Caller",
                importedPackage = "com.example.impl",
                importedClassName = "_Private_Helper",
            )
        ).isTrue()
        assertThat(
            rule.matches(
                importerPackage = "com.example.api",
                importerClassName = "Caller",
                importedPackage = "com.example.impl",
                importedClassName = "PublicHelper",
            )
        ).isFalse()
    }
}
