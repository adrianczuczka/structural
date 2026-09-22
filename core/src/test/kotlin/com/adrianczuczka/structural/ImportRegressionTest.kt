package com.adrianczuczka.structural

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ImportRegressionTest {
    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        File(projectDir, "settings.gradle.kts").writeText("")
        File(projectDir, "build.gradle.kts").writeText("""
            plugins { id("com.adrianczuczka.structural") }
            repositories { mavenCentral() }
        """.trimIndent())
    }

    private fun config(text: String) = File(projectDir, "structural.yml").writeText(text.trimIndent())

    private fun source(path: String, text: String) {
        File(projectDir, "src/main/$path").apply {
            parentFile.mkdirs()
            writeText(text.trimIndent())
        }
    }

    private fun runner() = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments("structuralCheck")

    @Test
    fun `broad dependency permissions survive separately tracked descendants`() {
        source("java/dev/ionfusion/fusion/Caller.java", """
            package dev.ionfusion.fusion;
            import dev.ionfusion.commons.resources.ResourcePosition;
            import dev.ionfusion.commons.attributes.Attribute;
            class Caller {}
        """)
        for (target in listOf("dev.ionfusion.commons", "dev.ionfusion.commons.**", "dev.ionfusion.*.resources")) {
            config("""
                rules:
                  "dev.ionfusion.fusion!":
                    - "$target"
                    - dev.ionfusion.commons.attributes
                  dev.ionfusion.commons.resources: []
                  dev.ionfusion.commons.attributes: []
            """)
            assertThat(runner().build().output).doesNotContain("cannot import")
        }
    }

    @Test
    fun `exact and single-star dependency permissions do not grant descendants or similar prefixes`() {
        source("java/dev/ionfusion/fusion/Caller.java", """
            package dev.ionfusion.fusion;
            import dev.ionfusion.commons.resources.ResourcePosition;
            import dev.ionfusion.commons.resources.internal.Hidden;
            import dev.ionfusion.commonsExtra.Hidden;
            class Caller {}
        """)
        config("""
            rules:
              "dev.ionfusion.fusion!":
                - "dev.ionfusion.commons.resources!"
              dev.ionfusion.commons.resources: []
              dev.ionfusion.commonsExtra: []
        """)
        val exact = runner().buildAndFail().output
        assertThat(exact).contains("2 import rule violation(s)")
        assertThat(exact).contains("cannot import from `dev.ionfusion.commons.resources.internal`")
        assertThat(exact).contains("cannot import from `dev.ionfusion.commonsExtra`")

        config("""
            rules:
              "dev.ionfusion.fusion!":
                - "dev.ionfusion.commons.*"
              dev.ionfusion.commons.resources: []
              dev.ionfusion.commonsExtra: []
        """)
        assertThat(runner().buildAndFail().output).contains("2 import rule violation(s)")
    }

    @Test
    fun `more specific importer still replaces parent permissions`() {
        config("""
            rules:
              dev.ionfusion.runtime:
                - dev.ionfusion.commons
              dev.ionfusion.runtime.base:
                - dev.ionfusion.runtime._private.util
        """)
        source("java/dev/ionfusion/runtime/base/Caller.java", """
            package dev.ionfusion.runtime.base;
            import dev.ionfusion.commons.resources.ResourcePosition;
            import dev.ionfusion.runtime._private.util.Helper;
            class Caller {}
        """)
        val output = runner().buildAndFail().output
        assertThat(output).contains("1 import rule violation(s)")
        assertThat(output).contains("cannot import from `dev.ionfusion.commons.resources`")
    }

    @Test
    fun `same-package nested Java imports work with overlapping exact and recursive rules`() {
        config("""
            rules:
              dev.ionfusion.fusion: []
              "dev.ionfusion.fusion!": []
        """)
        source("java/dev/ionfusion/fusion/FusionSymbol.java", """
            package dev.ionfusion.fusion;
            class FusionSymbol {
                static class BaseSymbol {
                    static final String VALUE = "symbol";
                    static void internSymbols() {}
                }
            }
        """)
        source("java/dev/ionfusion/fusion/FusionStruct.java", """
            package dev.ionfusion.fusion;
            import dev.ionfusion.fusion.FusionSymbol.BaseSymbol;
            import dev.ionfusion.fusion.FusionSymbol.*;
            import static dev.ionfusion.fusion.FusionSymbol.BaseSymbol.internSymbols;
            import static dev.ionfusion.fusion.FusionSymbol.BaseSymbol.*;
            class FusionStruct {}
        """)
        assertThat(runner().build().output).doesNotContain("cannot import")
    }

    @Test
    fun `nested types cannot bypass exact-package boundaries`() {
        config("""
            rules:
              "com.example.api!": []
              "com.example.impl!": []
        """)
        // The enclosing class does not share the source file's name.
        source("java/com/example/impl/Types.java", """
            package com.example.impl;
            class Outer {
                static class Inner {
                    static final int VALUE = 1;
                }
            }
        """)
        source("java/com/example/api/Caller.java", """
            package com.example.api;
            import com.example.impl.Outer.Inner;
            import com.example.impl.Outer.*;
            import static com.example.impl.Outer.Inner.VALUE;
            import static com.example.impl.Outer.Inner.*;
            class Caller {}
        """)
        val output = runner().buildAndFail().output
        assertThat(output).contains("4 import rule violation(s)")
        assertThat(output).contains("cannot import from `com.example.impl`")
    }

    @Test
    fun `Kotlin nested types and object members use the declared package`() {
        config("""
            rules:
              "com.example.api!": []
              "com.example.impl!": []
        """)
        source("kotlin/com/example/impl/Types.kt", """
            package com.example.impl
            class Outer { class Inner }
            object Utilities { val value = 1 }
        """)
        source("kotlin/com/example/api/Caller.kt", """
            package com.example.api
            import com.example.impl.Outer.Inner
            import com.example.impl.Utilities.value
            class Caller
        """)
        val output = runner().buildAndFail().output
        assertThat(output).contains("2 import rule violation(s)")
        assertThat(output).contains("cannot import from `com.example.impl`")
    }

    @Test
    fun `nested import resolution does not guess from capitalization`() {
        config("""
            rules:
              "com.Example.api!": []
              "com.Example.impl!": []
        """)
        source("java/com/Example/impl/Types.java", """
            package com.Example.impl;
            class outer { static class inner {} }
        """)
        source("java/com/Example/api/Caller.java", """
            package com.Example.api;
            import com.Example.impl.outer.inner;
            class Caller {}
        """)
        assertThat(runner().buildAndFail().output).contains("cannot import from `com.Example.impl`")
    }

    @Test
    fun `nested class exceptions preserve simple-name matching and never grant wildcard imports`() {
        config("""
            rules:
              "com.example.api!": []
              "com.example.impl!": []
            classAllowlist:
              "com.example.api.**":
                - com.example.impl.Inner
        """)
        source("java/com/example/impl/Outer.java", """
            package com.example.impl;
            class Outer { static class Inner { static final int VALUE = 1; } }
        """)
        source("java/com/example/api/Caller.java", """
            package com.example.api;
            import com.example.impl.Outer.Inner;
            import static com.example.impl.Outer.Inner.VALUE;
            import com.example.impl.Outer.*;
            import static com.example.impl.Outer.Inner.*;
            class Caller {}
        """)
        val output = runner().buildAndFail().output
        assertThat(output).contains("2 import rule violation(s)")
        assertThat(output).contains("Caller.java:4")
        assertThat(output).contains("Caller.java:5")
        assertThat(output).doesNotContain("Caller.java:2")
        assertThat(output).doesNotContain("Caller.java:3")
    }

    @Test
    fun `Kotlin wildcard imports cannot accidentally match a class allowlist`() {
        config("""
            rules:
              "com.example.api!": []
              "com.example.impl!": []
            classAllowlist:
              "com.example.api.**":
                - com.example.impl.Outer
        """)
        source("kotlin/com/example/impl/Outer.kt", """
            package com.example.impl
            class Outer { class Inner }
        """)
        source("kotlin/com/example/api/Caller.kt", """
            package com.example.api
            import com.example.impl.Outer.*
            class Caller
        """)
        assertThat(runner().buildAndFail().output).contains("1 import rule violation(s)")
    }
}
