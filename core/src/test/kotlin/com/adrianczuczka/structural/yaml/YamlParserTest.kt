package com.adrianczuczka.structural.yaml

import com.google.common.truth.Truth.assertThat
import org.gradle.api.GradleException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.io.path.createTempDirectory

class YamlParserTest {

    private lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        tempDir = createTempDirectory("yaml-parser-test").toFile()
    }

    @AfterEach
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun yamlFile(content: String): File =
        File(tempDir, "structural.yml").apply { writeText(content) }

    // --- Non-existent file ---

    @Test
    fun `returns null when file does not exist`() {
        val result = File(tempDir, "nonexistent.yml").parseYamlImportRules()
        assertThat(result).isNull()
    }

    // --- Arrow syntax ---

    @Test
    fun `parses left arrow rule correctly`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            rules:
              - data <- domain
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.checkedPackages).containsExactly("data", "domain")
        assertThat(result.rules["data"]).containsExactly("domain")
        assertThat(result.rules["domain"]).isEmpty()
    }

    @Test
    fun `parses right arrow rule correctly`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            rules:
              - domain -> data
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["data"]).containsExactly("domain")
        assertThat(result.rules["domain"]).isEmpty()
    }

    @Test
    fun `parses chained arrows correctly`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
              - ui
            rules:
              - data <- domain -> ui
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["data"]).containsExactly("domain")
        assertThat(result.rules["ui"]).containsExactly("domain")
    }

    @Test
    fun `parses long chain of arrows`() {
        val file = yamlFile(
            """
            packages:
              - a
              - b
              - c
              - d
            rules:
              - a <- b <- c <- d
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["a"]).containsExactly("b")
        assertThat(result.rules["b"]).containsExactly("c")
        assertThat(result.rules["c"]).containsExactly("d")
    }

    @Test
    fun `parses mixed arrow directions in chain`() {
        val file = yamlFile(
            """
            packages:
              - a
              - b
              - c
            rules:
              - a -> b <- c
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        // a -> b means b can import from a
        assertThat(result.rules["b"]).containsExactly("a", "c")
    }

    @Test
    fun `multiple arrow rules accumulate allowed imports`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
              - ui
            rules:
              - data <- domain
              - data <- ui
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["data"]).containsExactly("domain", "ui")
    }

    // --- Map syntax ---

    @Test
    fun `parses map-based rules`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
              - ui
            rules:
              data:
                - domain
                - ui
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["data"]).containsExactly("domain", "ui")
        assertThat(result.rules["domain"]).isEmpty()
        assertThat(result.rules["ui"]).isEmpty()
    }

    @Test
    fun `map syntax with single allowed import`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            rules:
              data:
                - domain
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["data"]).containsExactly("domain")
    }

    // --- Empty / missing rules ---

    @Test
    fun `empty rules list produces no allowed imports`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            rules: []
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["data"]).isEmpty()
        assertThat(result.rules["domain"]).isEmpty()
    }

    @Test
    fun `throws when rules key is missing`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            """.trimIndent()
        )
        val exception = assertThrows<GradleException> { file.parseYamlImportRules() }
        assertThat(exception.message).contains("No rules specified")
    }

    @Test
    fun `throws when packages list is missing`() {
        val file = yamlFile(
            """
            rules:
              - data <- domain
            """.trimIndent()
        )
        val exception = assertThrows<GradleException> { file.parseYamlImportRules() }
        assertThat(exception.message).contains("No packages specified")
    }

    @Test
    fun `throws when packages list is empty`() {
        val file = yamlFile(
            """
            packages: []
            rules:
              - data <- domain
            """.trimIndent()
        )
        val exception = assertThrows<GradleException> { file.parseYamlImportRules() }
        assertThat(exception.message).contains("No packages specified")
    }

    @Test
    fun `throws when rules is a string`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            rules: "some string"
            """.trimIndent()
        )
        val exception = assertThrows<GradleException> { file.parseYamlImportRules() }
        assertThat(exception.message).contains("Invalid rules format")
    }

    @Test
    fun `throws when arrow rule has no arrow`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            rules:
              - data domain
            """.trimIndent()
        )
        val exception = assertThrows<GradleException> { file.parseYamlImportRules() }
        assertThat(exception.message).contains("Invalid rule format")
    }

    @Test
    fun `throws when arrow rule has blank parts`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            rules:
              - "<- domain"
            """.trimIndent()
        )
        val exception = assertThrows<GradleException> { file.parseYamlImportRules() }
        assertThat(exception.message).contains("Invalid rule format")
    }

    // --- Packages with extra whitespace ---

    @Test
    fun `handles whitespace around arrow parts`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
            rules:
              - "data   <-   domain"
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["data"]).containsExactly("domain")
    }

    // --- Tracked package with zero rules ---

    @Test
    fun `tracked package with no rules gets empty allowed list`() {
        val file = yamlFile(
            """
            packages:
              - data
              - domain
              - ui
            rules:
              - data <- domain
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["ui"]).isEmpty()
    }

    // --- Duplicate packages ---

    @Test
    fun `duplicate packages in list are preserved`() {
        val file = yamlFile(
            """
            packages:
              - data
              - data
              - domain
            rules:
              - data <- domain
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.checkedPackages).containsExactly("data", "data", "domain")
    }

    // --- Bidirectional rules ---

    @Test
    fun `bidirectional rules with separate lines`() {
        val file = yamlFile(
            """
            packages:
              - a
              - b
            rules:
              - a <- b
              - b <- a
            """.trimIndent()
        )
        val result = file.parseYamlImportRules()!!
        assertThat(result.rules["a"]).containsExactly("b")
        assertThat(result.rules["b"]).containsExactly("a")
    }
}
