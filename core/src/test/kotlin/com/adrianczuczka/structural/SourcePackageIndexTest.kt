package com.adrianczuczka.structural

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SourcePackageIndexTest {
    private val index = SourcePackageIndex(listOf(
        ParsedSourceFile("com.Example.impl", emptyList(), listOf("outer", "Outer")),
        ParsedSourceFile("com.Example.impl.sub", emptyList(), listOf("Helper")),
    ))

    @Test
    fun `declared types resolve nested imports and members without naming conventions`() {
        for (path in listOf(
            "com.Example.impl.outer.inner",
            "com.Example.impl.Outer.Inner.Deep",
            "com.Example.impl.Outer.Inner.member",
            "com.Example.impl.Outer.Inner.*",
            "com.Example.impl.Outer",
        )) {
            for (isStatic in listOf(false, true)) {
                assertThat(index.importedPackage(ParsedImport(path, 1, null, isStatic)))
                    .isEqualTo("com.Example.impl")
            }
        }
    }

    @Test
    fun `declared subpackages and unknown dependencies retain their own packages`() {
        for ((path, expected) in listOf(
            "com.Example.impl.sub.Helper" to "com.Example.impl.sub",
            "com.Example.impl.unknown.External" to "com.Example.impl.unknown",
            "org.library.External" to "org.library",
            "com.Example.impl.*" to "com.Example.impl",
        )) {
            assertThat(index.importedPackage(ParsedImport(path, 1, null))).isEqualTo(expected)
        }
        assertThat(index.importedPackage(ParsedImport("org.library.External.MEMBER", 1, "MEMBER", true)))
            .isEqualTo("org.library")
    }
}
