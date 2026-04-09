package com.adrianczuczka.structural.baseline

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BaselineDataTest {

    @Test
    fun `toXml produces valid XML with violations`() {
        val baseline = BaselineData(
            listOf(
                "ForbiddenImport\$TestFile\$5\$com.example.ui\$com.example.data",
                "FileOnSameLevelAsPackages\$TestFile\$3\$SomeClass\$com.example"
            )
        )
        val xml = baseline.toXml()
        assertThat(xml).contains("<?xml version=\"1.0\" ?>")
        assertThat(xml).contains("<StructuralBaseline>")
        assertThat(xml).contains("<CurrentIssues>")
        assertThat(xml).contains("<ID>ForbiddenImport\$TestFile\$5\$com.example.ui\$com.example.data</ID>")
        assertThat(xml).contains("<ID>FileOnSameLevelAsPackages\$TestFile\$3\$SomeClass\$com.example</ID>")
        assertThat(xml).contains("</CurrentIssues>")
        assertThat(xml).contains("</StructuralBaseline>")
    }

    @Test
    fun `toXml with empty violations list produces valid XML`() {
        val baseline = BaselineData(emptyList())
        val xml = baseline.toXml()
        assertThat(xml).contains("<StructuralBaseline>")
        assertThat(xml).contains("<CurrentIssues>")
        assertThat(xml).contains("</CurrentIssues>")
        assertThat(xml).doesNotContain("<ID>")
    }

    @Test
    fun `toXml with single violation`() {
        val baseline = BaselineData(
            listOf("ForbiddenImport\$Test\$1\$com.a\$com.b")
        )
        val xml = baseline.toXml()
        assertThat(xml).contains("<ID>ForbiddenImport\$Test\$1\$com.a\$com.b</ID>")
    }

    @Test
    fun `toXml round-trips through XML parser`() {
        val violations = listOf(
            "ForbiddenImport\$TestFile\$5\$com.example.ui\$com.example.data",
            "FileOnSameLevelAsPackages\$TestFile\$3\$SomeClass\$com.example"
        )
        val baseline = BaselineData(violations)
        val xml = baseline.toXml()

        // Parse the XML back
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        document.documentElement.normalize()

        val idNodes = document.getElementsByTagName("ID")
        val parsedViolations = (0 until idNodes.length).map { idNodes.item(it).textContent.trim() }

        assertThat(parsedViolations).containsExactlyElementsIn(violations)
    }

    @Test
    fun `toXml handles special characters in violation IDs`() {
        val baseline = BaselineData(
            listOf("ForbiddenImport\$MyFile\$10\$com.example.ui\$com.example.data.local")
        )
        val xml = baseline.toXml()
        assertThat(xml).contains("ForbiddenImport\$MyFile\$10\$com.example.ui\$com.example.data.local")

        // Verify it's still parseable XML
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        assertThat(document.getElementsByTagName("ID").length).isEqualTo(1)
    }
}
