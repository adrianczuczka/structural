package com.adrianczuczka.structural


import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class StructuralPluginTest {

    private lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        testProjectDir = createTempDirectory("gradle-test-project").toFile()
        File(testProjectDir, "settings.gradle.kts").writeText("")
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.adrianczuczka.structural")
            }
            repositories {
                mavenCentral()
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
            repositories {
                mavenCentral()
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
        repositories {
            mavenCentral()
        }
        """
        )

        // Add build.gradle.kts to moduleB
        File(moduleB, "build.gradle.kts").writeText(
            """
        plugins {
            id("com.adrianczuczka.structural")
        }
        repositories {
            mavenCentral()
        }
        """
        )

        // Add structural.yml to moduleB
        File(moduleB, "structural.yml").writeText(
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

        assertThat(result.output).contains("`com.example.ui` cannot import from `com.example.data`")
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

    @Test
    fun `structuralCheck should detect violations with multi-segment package names`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
                - "com.example.app.core"
                - "com.example.app.service"
                - "com.example.app.util"

            rules:
                "com.example.app.core":
                    - "com.example.app.service"
                    - "com.example.app.util"
                "com.example.app.service":
                    - "com.example.app.util"
            """
        )

        File(testProjectDir, "src/main/java/com/example/app/service/MyService.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.app.service;

                import com.example.app.core.CoreClass;

                public class MyService {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.app.service` cannot import from `com.example.app.core`")
    }

    @Test
    fun `structuralCheck should pass valid imports with multi-segment package names`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
                - "com.example.app.core"
                - "com.example.app.service"
                - "com.example.app.util"

            rules:
                "com.example.app.core":
                    - "com.example.app.service"
                    - "com.example.app.util"
                "com.example.app.service":
                    - "com.example.app.util"
            """
        )

        File(testProjectDir, "src/main/java/com/example/app/service/MyService.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.app.service;

                import com.example.app.util.Helper;

                public class MyService {}
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
    fun `structuralCheck should allow imports within same multi-segment package`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
                - "com.example.app.core"
                - "com.example.app.service"
                - "com.example.app.util"

            rules:
                "com.example.app.core":
                    - "com.example.app.service"
                    - "com.example.app.util"
                "com.example.app.service":
                    - "com.example.app.util"
            """
        )

        File(testProjectDir, "src/main/java/com/example/app/service/sub/Test.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.app.service.sub;

                import com.example.app.service.MyService;

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
    fun `structuralCheck should fail when a rule has no arrow`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - data
              - domain

            rules:
              - data
            """
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("Invalid rule format: 'data'. Rules must contain <- or -> arrows.")
    }

    @Test
    fun `structuralCheck should fail when rules is a string instead of a list or map`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - data
              - domain

            rules: "some string"
            """
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("Invalid rules format in config file. Rules must be a list of arrow rules or a map of package dependencies.")
    }

    @Test
    fun `structuralCheck should work with map-based syntax and single-segment packages`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - data
              - domain
              - ui

            rules:
              data:
                - domain
              ui:
                - domain
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.SomeClass

                class Test
                """
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
    fun `structuralGenerateBaseline should produce a baseline file`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.SomeClass

                class Test
                """
            )
        }

        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralGenerateBaseline")
            .build()

        val baselineFile = File(testProjectDir, "baseline.xml")
        assertThat(baselineFile.exists()).isTrue()
        assertThat(baselineFile.readText()).contains("ForbiddenImport")
    }

    @Test
    fun `structuralCheck should ignore violations present in baseline`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.SomeClass

                class Test
                """
            )
        }

        // Generate baseline first
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralGenerateBaseline")
            .build()

        // Now check should pass because violations are baselined
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build()

        assertThat(result.output).doesNotContain("cannot import")
    }

    @Test
    fun `structuralCheck round-trip - generate baseline then check passes`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.SomeClass

                class Test
                """
            )
        }

        // Generate baseline
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralGenerateBaseline")
            .build()

        val baselineFile = File(testProjectDir, "baseline.xml")
        assertThat(baselineFile.exists()).isTrue()

        // Check passes with baseline
        val checkResult = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build()

        assertThat(checkResult.output).contains("All package imports follow the specified package rules")
    }

    @Test
    fun `structuralCheck baseline should not break when lines shift`() {
        val testFile = File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.SomeClass

                class Test
                """
            )
        }

        // Generate baseline with the violation
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralGenerateBaseline")
            .build()

        // Add a new import before the violation, shifting its line number
        testFile.writeText(
            """
            package com.example.ui

            import com.example.ui.SomeOtherClass
            import com.example.data.SomeClass

            class Test
            """
        )

        // Check should still pass — the violation is the same despite the line shift
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build()

        assertThat(result.output).contains("All package imports follow the specified package rules")
    }

    @Test
    fun `structuralGenerateBaseline should produce sorted deterministic output`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Zebra.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.ZebraClass

                class Zebra
                """
            )
        }
        File(testProjectDir, "src/main/kotlin/com/example/ui/Alpha.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.AlphaClass

                class Alpha
                """
            )
        }

        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralGenerateBaseline")
            .build()

        val baselineFile = File(testProjectDir, "baseline.xml")
        val content = baselineFile.readText()
        val idPattern = Regex("<ID>(.*?)</ID>")
        val ids = idPattern.findAll(content).map { it.groupValues[1] }.toList()

        assertThat(ids).isEqualTo(ids.sorted())
    }

    @Test
    fun `structuralCheck baseline should catch new imports from same forbidden package`() {
        val testFile = File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.SomeClass

                class Test
                """
            )
        }

        // Generate baseline with one violation
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralGenerateBaseline")
            .build()

        // Add a second import from the same forbidden package
        testFile.writeText(
            """
            package com.example.ui

            import com.example.data.SomeClass
            import com.example.data.AnotherClass

            class Test
            """
        )

        // Check should fail — AnotherClass is a new violation not in the baseline
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("cannot import")
    }

    @Test
    fun `structuralCheck error message should include total violation count`() {
        File(testProjectDir, "src/main/kotlin/com/example/ui/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.ui

                import com.example.data.SomeClass
                import com.example.test.OtherClass

                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("2 import rule violation(s) detected in 1 file(s).")
    }

    @Test
    fun `structuralCheck should enforce exact match when package is suffixed with bang`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
                - "com.example.api!"
                - "com.example.impl"

            rules:
                - "com.example.api! -> com.example.impl"
            """
        )

        File(testProjectDir, "src/main/java/com/example/api/sub/Deep.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api.sub;

                import com.example.impl.Hidden;

                public class Deep {}
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
    fun `structuralCheck should match exact package when suffixed with bang on importing side`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
                - "com.example.api!"
                - "com.example.impl!"

            rules: []
            """
        )

        File(testProjectDir, "src/main/java/com/example/api/Api.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api;

                import com.example.impl.Hidden;

                public class Api {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.api` cannot import from `com.example.impl`")
    }

    @Test
    fun `structuralCheck should allow imports via explicit trailing double-star`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
                - "com.example.api.**"
                - "com.example.impl.**"

            rules:
                - "com.example.impl.** -> com.example.api.**"
            """
        )

        File(testProjectDir, "src/main/java/com/example/api/sub/Api.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api.sub;

                import com.example.impl.deep.Hidden;

                public class Api {}
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
    fun `structuralCheck should honor single-star wildcard for direct children only`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
                - "com.example.core"
                - "com.example.*.plugin"

            rules: []
            """
        )

        File(testProjectDir, "src/main/java/com/example/core/Core.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.core;

                import com.example.x.plugin.Plug;

                public class Core {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.core` cannot import from `com.example.x.plugin`")
    }

    @Test
    fun `structuralCheck should match mid-path double-star wildcard`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
                - "com.example.api"
                - "com.**.internal"

            rules: []
            """
        )

        File(testProjectDir, "src/main/java/com/example/api/Api.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api;

                import com.foo.bar.internal.Secret;

                public class Api {}
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("`com.example.api` cannot import from `com.foo.bar.internal`")
    }

    @Test
    fun `structuralCheck should fail when using bang on single-segment token`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - data!
              - domain

            rules:
              - data! -> domain
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/data/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.data

                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("data!")
    }

    @Test
    fun `structuralCheck should fail when using wildcard on single-segment token`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - "data*"
              - domain

            rules:
              - "data* -> domain"
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/data/Test.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.data

                class Test
                """
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("data*")
    }

    // region: class rules

    @Test
    fun `class rule grants single-class import in Kotlin`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/api/Caller.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.FusionException

                class Caller
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
    fun `class rule allows only the specified class not other classes in the same package`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/api/Caller.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.OtherException

                class Caller
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("cannot import from `com.example.impl`")
    }

    @Test
    fun `class rule grants single-class import in Java`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        )

        File(testProjectDir, "src/main/java/com/example/api/Caller.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api;

                import com.example.impl.FusionException;

                public class Caller {}
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
    fun `importer-class restriction limits which file gets permission`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.ApiBuilder <- com.example.impl.**"
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/api/ApiBuilder.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.Helper

                class ApiBuilder
                """.trimIndent()
            )
        }
        File(testProjectDir, "src/main/kotlin/com/example/api/OtherApi.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.Helper

                class OtherApi
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        // OtherApi.kt should be flagged but ApiBuilder.kt should not
        assertThat(result.output).contains("OtherApi.kt")
        assertThat(result.output).contains("1 import rule violation(s) detected in 1 file(s)")
    }

    @Test
    fun `class rule matches Java static import via enclosing class`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.** <- com.example.impl.Util"
            """
        )

        File(testProjectDir, "src/main/java/com/example/api/Caller.java").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api;

                import static com.example.impl.Util.LOG;

                public class Caller {}
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
    fun `class-name glob matches imports starting with prefix`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.** <- com.example.impl._Private_*"
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/api/Caller.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl._Private_Helper

                class Caller
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
    fun `class-name glob does not match classes outside the prefix`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.** <- com.example.impl._Private_*"
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/api/Caller.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.PublicHelper

                class Caller
                """.trimIndent()
            )
        }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .buildAndFail()

        assertThat(result.output).contains("cannot import from `com.example.impl`")
    }

    @Test
    fun `class rule across deep subpackages matches`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.** <- com.example.impl.**._Private_*"
            """
        )

        File(testProjectDir, "src/main/kotlin/com/example/api/Caller.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.deep._Private_X

                class Caller
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
    fun `class rule cannot deny what package rule allows`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules:
              - "com.example.api <- com.example.impl"

            classes:
              - "com.example.api.** <- com.example.impl.OnlyThisOne"
            """
        )

        // Package rule already allows api <- impl. Class rule mentioning a specific
        // class should NOT restrict the package rule.
        File(testProjectDir, "src/main/kotlin/com/example/api/Caller.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.SomeOtherClass

                class Caller
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
    fun `baseline coexists with class rules`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.api
              - com.example.impl

            rules: []

            classes:
              - "com.example.api.** <- com.example.impl.FusionException"
            """
        )

        // First file: granted by class rule (no violation)
        File(testProjectDir, "src/main/kotlin/com/example/api/Caller.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.FusionException

                class Caller
                """.trimIndent()
            )
        }
        // Second file: NOT granted by class rule, will be in baseline
        File(testProjectDir, "src/main/kotlin/com/example/api/OtherCaller.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.api

                import com.example.impl.OtherClass

                class OtherCaller
                """.trimIndent()
            )
        }

        // Generate baseline — should record only the un-granted import
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralGenerateBaseline")
            .build()

        val baselineFile = File(testProjectDir, "baseline.xml")
        assertThat(baselineFile.exists()).isTrue()
        assertThat(baselineFile.readText()).contains("OtherClass")
        assertThat(baselineFile.readText()).doesNotContain("FusionException")

        // structuralCheck against the baseline should now pass
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("structuralCheck")
            .build()

        assertThat(result.output).doesNotContain("cannot import")
    }

    @Test
    fun `same-package imports remain auto-allowed regardless of class rules`() {
        File(testProjectDir, "structural.yml").writeText(
            """
            packages:
              - com.example.app

            rules: []

            classes:
              - "com.example.app.api.ApiBuilder <- com.example.app.impl.**"
            """
        )

        // File in subpackage importing from sibling subpackage — same tracked package hierarchy
        File(testProjectDir, "src/main/kotlin/com/example/app/api/Foo.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.app.api

                import com.example.app.impl.Bar

                class Foo
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

    // endregion
}

