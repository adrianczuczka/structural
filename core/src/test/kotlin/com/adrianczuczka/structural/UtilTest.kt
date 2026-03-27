package com.adrianczuczka.structural

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class UtilTest {

    private lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("util-test").toFile()
    }

    @AfterEach
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    // ===== extractPackageFromImport =====

    @Nested
    inner class ExtractPackageFromImportTest {

        @Test
        fun `extracts package from simple import`() {
            val result = extractPackageFromImport("com.example.data.SomeClass")
            assertThat(result).isEqualTo("com.example.data")
        }

        @Test
        fun `extracts package from deep import`() {
            val result = extractPackageFromImport("com.example.app.data.local.db.Entity")
            assertThat(result).isEqualTo("com.example.app.data.local.db")
        }

        @Test
        fun `returns import as-is when single segment`() {
            val result = extractPackageFromImport("SomeClass")
            assertThat(result).isEqualTo("SomeClass")
        }

        @Test
        fun `extracts package from two-segment import`() {
            val result = extractPackageFromImport("data.SomeClass")
            assertThat(result).isEqualTo("data")
        }

        @Test
        fun `handles wildcard import`() {
            val result = extractPackageFromImport("com.example.data.*")
            assertThat(result).isEqualTo("com.example.data")
        }

        @Test
        fun `handles static import by dropping two segments`() {
            val result = extractPackageFromImport("com.example.data.SomeClass.someMethod", isStatic = true)
            assertThat(result).isEqualTo("com.example.data")
        }

        @Test
        fun `static import with only two segments drops one`() {
            val result = extractPackageFromImport("SomeClass.someMethod", isStatic = true)
            assertThat(result).isEqualTo("SomeClass")
        }

        @Test
        fun `static import with three segments drops two`() {
            val result = extractPackageFromImport("data.SomeClass.someMethod", isStatic = true)
            assertThat(result).isEqualTo("data")
        }
    }

    // ===== Java file parsing =====

    @Nested
    inner class JavaParsingTest {

        @Test
        fun `parses Java file with package and imports`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """
                    package com.example.ui;

                    import com.example.data.Repository;
                    import com.example.domain.UseCase;

                    public class Test {}
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isEqualTo("com.example.ui")
            assertThat(result.imports).hasSize(2)
            assertThat(result.imports[0].importPath).isEqualTo("com.example.data.Repository")
            assertThat(result.imports[0].className).isEqualTo("Repository")
            assertThat(result.imports[0].isStatic).isFalse()
            assertThat(result.imports[1].importPath).isEqualTo("com.example.domain.UseCase")
        }

        @Test
        fun `parses Java file with no package declaration`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """
                    import com.example.data.Repository;

                    public class Test {}
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isNull()
            assertThat(result.imports).hasSize(1)
        }

        @Test
        fun `parses Java file with no imports`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """
                    package com.example.ui;

                    public class Test {}
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isEqualTo("com.example.ui")
            assertThat(result.imports).isEmpty()
        }

        @Test
        fun `parses Java static import`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """
                    package com.example.ui;

                    import static com.example.data.Constants.MAX_SIZE;

                    public class Test {}
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].isStatic).isTrue()
            assertThat(result.imports[0].importPath).isEqualTo("com.example.data.Constants.MAX_SIZE")
            assertThat(result.imports[0].className).isEqualTo("MAX_SIZE")
        }

        @Test
        fun `parses Java wildcard import`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """
                    package com.example.ui;

                    import com.example.data.*;

                    public class Test {}
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].importPath).isEqualTo("com.example.data.*")
            assertThat(result.imports[0].className).isNull()
        }

        @Test
        fun `parses Java file with comments before package`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """
                    // This is a comment
                    /* Another comment */
                    package com.example.ui;

                    import com.example.data.Repository;

                    public class Test {}
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isEqualTo("com.example.ui")
            assertThat(result.imports).hasSize(1)
        }

        @Test
        fun `ignores import-like text in string literals`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """
                    package com.example.ui;

                    import com.example.data.Repository;

                    public class Test {
                        String s = "import com.example.domain.Fake;";
                    }
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            // The regex requires the line to start with optional whitespace then "import",
            // so inline string content should not match since it has quotes before it
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].importPath).isEqualTo("com.example.data.Repository")
        }

        @Test
        fun `records correct line numbers for Java imports`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """package com.example.ui;

import com.example.data.A;
import com.example.data.B;

public class Test {}
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.imports[0].lineNumber).isEqualTo(3)
            assertThat(result.imports[1].lineNumber).isEqualTo(4)
        }

        @Test
        fun `parses empty Java file`() {
            val file = File(tempDir, "Test.java").apply {
                writeText("")
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isNull()
            assertThat(result.imports).isEmpty()
        }

        @Test
        fun `parses Java static wildcard import`() {
            val file = File(tempDir, "Test.java").apply {
                writeText(
                    """
                    package com.example.ui;

                    import static com.example.data.Constants.*;

                    public class Test {}
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].isStatic).isTrue()
            assertThat(result.imports[0].importPath).isEqualTo("com.example.data.Constants.*")
            assertThat(result.imports[0].className).isNull()
        }
    }

    // ===== Kotlin file parsing =====

    @Nested
    inner class KotlinParsingTest {

        @Test
        fun `parses Kotlin file with package and imports`() {
            val file = File(tempDir, "Test.kt").apply {
                writeText(
                    """
                    package com.example.ui

                    import com.example.data.Repository
                    import com.example.domain.UseCase

                    class Test
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isEqualTo("com.example.ui")
            assertThat(result.imports).hasSize(2)
            assertThat(result.imports[0].importPath).isEqualTo("com.example.data.Repository")
            assertThat(result.imports[1].importPath).isEqualTo("com.example.domain.UseCase")
        }

        @Test
        fun `parses Kotlin file with no package declaration`() {
            val file = File(tempDir, "Test.kt").apply {
                writeText(
                    """
                    import com.example.data.Repository

                    class Test
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isNull()
            assertThat(result.imports).hasSize(1)
        }

        @Test
        fun `parses Kotlin file with no imports`() {
            val file = File(tempDir, "Test.kt").apply {
                writeText(
                    """
                    package com.example.ui

                    class Test
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isEqualTo("com.example.ui")
            assertThat(result.imports).isEmpty()
        }

        @Test
        fun `parses Kotlin wildcard import`() {
            val file = File(tempDir, "Test.kt").apply {
                writeText(
                    """
                    package com.example.ui

                    import com.example.data.*

                    class Test
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].importPath).isEqualTo("com.example.data")
        }

        @Test
        fun `parses Kotlin alias import`() {
            val file = File(tempDir, "Test.kt").apply {
                writeText(
                    """
                    package com.example.ui

                    import com.example.data.Repository as Repo

                    class Test
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].importPath).isEqualTo("com.example.data.Repository")
        }

        @Test
        fun `parses empty Kotlin file`() {
            val file = File(tempDir, "Test.kt").apply {
                writeText("")
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isNull()
            assertThat(result.imports).isEmpty()
        }

        @Test
        fun `parses Kotlin file with multiple classes`() {
            val file = File(tempDir, "Test.kt").apply {
                writeText(
                    """
                    package com.example.ui

                    import com.example.data.Repository

                    class TestA
                    class TestB
                    data class TestC(val x: Int)
                    """.trimIndent()
                )
            }
            val result = file.parseSourceFile()
            assertThat(result.packageName).isEqualTo("com.example.ui")
            assertThat(result.imports).hasSize(1)
        }
    }

    // ===== getIgnoredViolationsFromBaseline =====

    @Nested
    inner class BaselineParsingTest {

        @Test
        fun `returns empty map when file does not exist`() {
            val result = getIgnoredViolationsFromBaseline(File(tempDir, "nonexistent.xml").absolutePath)
            assertThat(result).isEmpty()
        }

        @Test
        fun `parses ForbiddenImport violations from baseline`() {
            val file = File(tempDir, "baseline.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" ?>
                    <StructuralBaseline>
                      <CurrentIssues>
                        <ID>ForbiddenImport${'$'}TestFile${'$'}5${'$'}com.example.ui${'$'}com.example.data</ID>
                      </CurrentIssues>
                    </StructuralBaseline>
                    """.trimIndent()
                )
            }
            val result = getIgnoredViolationsFromBaseline(file.absolutePath)
            assertThat(result).hasSize(1)
            assertThat(result["TestFile"]).hasSize(1)
            val violation = result["TestFile"]!![0] as ViolationData.ForbiddenImport
            assertThat(violation.lineNumber).isEqualTo(5)
            assertThat(violation.importingPackage).isEqualTo("com.example.ui")
            assertThat(violation.importedPackage).isEqualTo("com.example.data")
        }

        @Test
        fun `parses FileOnSameLevelAsPackages violations from baseline`() {
            val file = File(tempDir, "baseline.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" ?>
                    <StructuralBaseline>
                      <CurrentIssues>
                        <ID>FileOnSameLevelAsPackages${'$'}TestFile${'$'}3${'$'}SomeClass${'$'}com.example</ID>
                      </CurrentIssues>
                    </StructuralBaseline>
                    """.trimIndent()
                )
            }
            val result = getIgnoredViolationsFromBaseline(file.absolutePath)
            assertThat(result).hasSize(1)
            val violation = result["TestFile"]!![0] as ViolationData.FileOnSameLevelAsPackages
            assertThat(violation.lineNumber).isEqualTo(3)
            assertThat(violation.className).isEqualTo("SomeClass")
            assertThat(violation.importedPackage).isEqualTo("com.example")
        }

        @Test
        fun `parses multiple violations for same file`() {
            val file = File(tempDir, "baseline.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" ?>
                    <StructuralBaseline>
                      <CurrentIssues>
                        <ID>ForbiddenImport${'$'}TestFile${'$'}5${'$'}com.example.ui${'$'}com.example.data</ID>
                        <ID>ForbiddenImport${'$'}TestFile${'$'}6${'$'}com.example.ui${'$'}com.example.domain</ID>
                      </CurrentIssues>
                    </StructuralBaseline>
                    """.trimIndent()
                )
            }
            val result = getIgnoredViolationsFromBaseline(file.absolutePath)
            assertThat(result["TestFile"]).hasSize(2)
        }

        @Test
        fun `parses violations across multiple files`() {
            val file = File(tempDir, "baseline.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" ?>
                    <StructuralBaseline>
                      <CurrentIssues>
                        <ID>ForbiddenImport${'$'}FileA${'$'}5${'$'}com.example.ui${'$'}com.example.data</ID>
                        <ID>ForbiddenImport${'$'}FileB${'$'}3${'$'}com.example.domain${'$'}com.example.data</ID>
                      </CurrentIssues>
                    </StructuralBaseline>
                    """.trimIndent()
                )
            }
            val result = getIgnoredViolationsFromBaseline(file.absolutePath)
            assertThat(result).hasSize(2)
            assertThat(result["FileA"]).hasSize(1)
            assertThat(result["FileB"]).hasSize(1)
        }

        @Test
        fun `returns empty map for empty baseline`() {
            val file = File(tempDir, "baseline.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" ?>
                    <StructuralBaseline>
                      <CurrentIssues>
                      </CurrentIssues>
                    </StructuralBaseline>
                    """.trimIndent()
                )
            }
            val result = getIgnoredViolationsFromBaseline(file.absolutePath)
            assertThat(result).isEmpty()
        }

        @Test
        fun `skips ID entries with fewer than 5 parts`() {
            val file = File(tempDir, "baseline.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" ?>
                    <StructuralBaseline>
                      <CurrentIssues>
                        <ID>MalformedEntry${'$'}OnlyThree${'$'}Parts</ID>
                        <ID>ForbiddenImport${'$'}TestFile${'$'}5${'$'}com.example.ui${'$'}com.example.data</ID>
                      </CurrentIssues>
                    </StructuralBaseline>
                    """.trimIndent()
                )
            }
            val result = getIgnoredViolationsFromBaseline(file.absolutePath)
            assertThat(result).hasSize(1)
            assertThat(result["TestFile"]).hasSize(1)
        }

        @Test
        fun `skips unknown violation types`() {
            val file = File(tempDir, "baseline.xml").apply {
                writeText(
                    """
                    <?xml version="1.0" ?>
                    <StructuralBaseline>
                      <CurrentIssues>
                        <ID>UnknownType${'$'}TestFile${'$'}5${'$'}foo${'$'}bar</ID>
                      </CurrentIssues>
                    </StructuralBaseline>
                    """.trimIndent()
                )
            }
            val result = getIgnoredViolationsFromBaseline(file.absolutePath)
            assertThat(result).isEmpty()
        }
    }
}
