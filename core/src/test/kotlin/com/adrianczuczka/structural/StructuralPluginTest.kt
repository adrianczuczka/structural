package com.adrianczuczka.structural


import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class StructuralPluginTest {

    private lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        testProjectDir = createTempDir("gradle-test-project")
        File(testProjectDir, "settings.gradle.kts").writeText("")
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.adrianczuczka.structural")
            }
            """
        )
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - data
              - domain
              - ui
              - test

            rules:
              - data <- domain -> ui
              - test -> data
            """
        )
    }

    @AfterEach
    fun cleanup() {
        testProjectDir.deleteRecursively()
    }

    @Test
    fun `plugin applies successfully`() {
        val result: BuildResult = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks")
            .build()

        assertThat(result.output).contains("structuralCheck")
    }

    @Test
    fun `structuralCheck should fail when an invalid import is found`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui
                
                import com.example.data.SomeClass // 🚨 Forbidden import
                
                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail() // Expect failure

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data`")
    }

    @Test
    fun `structuralCheck should fail when an invalid nested import is found`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui
                
                import com.example.data.local.SomeClass // 🚨 Forbidden import
                
                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail() // Expect failure

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data.local`")
    }

    @Test
    fun `structuralCheck should pass when a valid import is found`() {
        File(testProjectDir, "src/main/kotlin/com/example/data/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.data
                
                import com.example.domain.SomeClass // ✅ Allowed import
                
                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build() // Should pass

        assertThat(result.output).doesNotContain("cannot import")
    }

    @Test
    fun `structuralCheck should fail when a wildcard import is used`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui
                
                import com.example.data.* // 🚨 Wildcard import
                
                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail() // Expect failure

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data`")
    }

    @Test
    fun `structuralCheck should fail when yml file is missing`() {
        File(testProjectDir, "structural.yml").delete() // Remove structural.yml

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail() // Expect failure

        assertThat(result.output).contains("Could not find config file")
    }

    @Test
    fun `structuralCheck should fail when there are no rules but imports exist`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - data
              - domain
              - ui
              - test
            rules: []
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui
                
                import com.example.data.SomeClass // 🚨 No rules allow this
                
                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail() // Expect failure

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data`")
    }

    @Test
    fun `structuralCheck should pass when an import is from an untracked package`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui
                
                import com.external.lib.SomeClass // ✅ Allowed, because 'com.external.lib' is not tracked
                
                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build() // Should pass

        assertThat(result.output).doesNotContain("cannot import")
    }

    @Test
    fun `structuralCheck should respect custom structural yml path`() {
        File(testProjectDir, "build.gradle.kts").delete()
        File(testProjectDir, "custom-structural.yml").delete()
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.adrianczuczka.structural")
            }
            
            structural {
                config = "${'$'}rootDir/custom-structural.yml"
            }
            """
        )
        File(testProjectDir, "custom-structural.yml").writeText(
            """
            packages:
              - data
              - domain
              - ui
              - test

            rules:
              - data <- domain -> ui
              - test -> data
            """
        )


        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui
                
                import com.example.data.SomeClass // 🚨 Forbidden import
                
                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail() // Expect failure

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data`")
    }

    @Test
    fun `structuralCheck should always allow cross-module imports`() {
        val moduleA = File(testProjectDir, "moduleA").apply { mkdirs() }
        val moduleB = File(testProjectDir, "moduleB").apply { mkdirs() }

        // Update settings.gradle.kts to include modules
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
        rootProject.name = "test-project"
        include(":moduleA", ":moduleB")
        """
        )

        // Add build.gradle.kts to moduleA
        File(moduleA, "build.gradle.kts").writeText(
            """
        plugins {
            id("com.adrianczuczka.structural")
        }
        """
        )

        // Add build.gradle.kts to moduleB
        File(moduleB, "build.gradle.kts").writeText(
            """
        plugins {
            id("com.adrianczuczka.structural")
        }
        """
        )

        // UI should be able to import from Data if they are in separate modules
        File(moduleB, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
            package com.example.ui
            
            import com.example.data.SomeClass // Not allowed
            
            class Test
            """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments(":moduleB:structuralCheck")
            .buildAndFail() // Should pass

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data`")
    }


    @Test
    fun `structuralCheck should remain performant with many files`() {
        val kotlinFiles = mutableListOf<File>()

        // Generate 100 Kotlin files
        repeat(100) { index ->
            val file = File(testProjectDir, "src/main/kotlin/com/example/data/Test$index.kt").apply {
                parentFile.mkdirs()
                writeText(
                    """
                    package com.example.data
                    
                    import com.example.domain.SomeClass // ✅ Allowed
                    
                    class Test$index
                    """
                )
            }
            kotlinFiles.add(file)
        }

        val startTime = System.currentTimeMillis()
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build() // Should pass

        val endTime = System.currentTimeMillis()
        val executionTime = endTime - startTime

        assertThat(result.output).doesNotContain("cannot import")

        // Ensure execution time is reasonable
        assertThat(executionTime).isLessThan(5000) // Should finish within 5 seconds
    }

    @Test
    fun `structuralCheck should fail when file in nested package has invalid import`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/home/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui.home

                import com.example.data.SomeClass // 🚨 Forbidden import (ui cannot import data)

                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.ui.home` cannot import from `com.example.data`")
    }

    @Test
    fun `structuralCheck should pass when file in nested package has valid import`() {
        File(testProjectDir, "src/main/kotlin/com/example/data/local/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.data.local

                import com.example.domain.SomeClass // ✅ Allowed (data <- domain)

                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build()

        assertThat(result.output).doesNotContain("cannot import")
    }

    @Test
    fun `structuralCheck should allow imports within same tracked package hierarchy`() {
        File(testProjectDir, "src/main/kotlin/com/example/data/local/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.data.local

                import com.example.data.remote.SomeClass // ✅ Same tracked package (data)

                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build()

        assertThat(result.output).doesNotContain("cannot import")
    }

    @Test
    fun `structuralCheck should check each package layer independently`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - data
              - local
              - domain
              - ui

            rules:
              - data <- domain -> ui
              - local <- domain
            """
        )

        // File in data.local importing from data.remote — local layer should flag this
        File(testProjectDir, "src/main/kotlin/com/example/data/local/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.data.local

                import com.example.data.remote.SomeClass // 🚨 local cannot import remote

                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.data.local` cannot import from `com.example.data.remote`")
    }

    @Test
    fun `structuralCheck should not allow files on the same level as tracked packages`() {
        File(testProjectDir, "src/main/kotlin/com/example/data/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                    package com.example.data
                    
                    import com.example.SameLevelAsPackageClass // Not allowed
                    
                    class Test
                    """
            )
        }
        File(testProjectDir, "src/main/kotlin/com/example/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                    package com.example
                                        
                    class SameLevelAsPackageClass
                    """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail() // Should fail


        assertThat(result.output).contains("class \"SameLevelAsPackageClass\" is on the same level as \"com.example\" package. Move into a package")
    }

    @Test
    fun `structuralCheck should fail when a Java file has an invalid import`() {
        File(testProjectDir, "src/main/java/com/example/ui/Test.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui;

                import com.example.data.SomeClass;

                public class Test {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data`")
    }

    @Test
    fun `structuralCheck should pass when a Java file has a valid import`() {
        File(testProjectDir, "src/main/java/com/example/data/Test.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.data;

                import com.example.domain.SomeClass;

                public class Test {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build()

        assertThat(result.output).doesNotContain("cannot import")
    }

    @Test
    fun `structuralCheck should fail when a Java file has an invalid static import`() {
        File(testProjectDir, "src/main/java/com/example/ui/Test.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui;

                import static com.example.data.SomeClass.someMethod;

                public class Test {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data.SomeClass`")
    }

    @Test
    fun `structuralCheck should fail when a Java file has an invalid wildcard import`() {
        File(testProjectDir, "src/main/java/com/example/ui/Test.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui;

                import com.example.data.*;

                public class Test {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data`")
    }
}

