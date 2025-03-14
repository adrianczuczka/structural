package com.adrianczuczka.structural

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

internal fun File.parseKotlinFile(): KtFile =
    PsiFactoryProvider.ktPsiFactory.createFile(readText())

internal fun KtFile.extractPackageName(): String? =
    packageFqName.asString().takeIf { it.isNotEmpty() }

internal fun extractPackageFromImport(importPath: String): String {
    val parts = importPath.split(".")
    return parts.dropLast(1).joinToString(".")
}

internal fun KtImportDirective.getLineNumber(): Int =
    containingKtFile.viewProvider.document.getLineNumber(textRange.startOffset) + 1

internal fun getIgnoredViolationsFromBaseline(baselinePath: String): Set<String> {
    val file = File(baselinePath)
    if (!file.exists()) return emptySet()

    val document: Document =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)

    document.documentElement.normalize()

    val violationsNodeList: NodeList = document.getElementsByTagName("ID")

    val violations = mutableSetOf<String>()
    for (i in 0 until violationsNodeList.length) {
        val node = violationsNodeList.item(i)
        if (node is Element) {
            violations += node.textContent.trim()
        }
    }
    return violations
}