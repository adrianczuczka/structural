package com.adrianczuczka.structural

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

internal data class ParsedImport(
    val importPath: String,
    val lineNumber: Int,
    val className: String?
)

internal data class ParsedSourceFile(
    val packageName: String?,
    val imports: List<ParsedImport>
)

internal fun File.parseSourceFile(): ParsedSourceFile =
    if (extension == "java") parseJavaSourceFile() else parseKotlinSourceFile()

private fun File.parseKotlinSourceFile(): ParsedSourceFile {
    val ktFile = PsiFactoryProvider.ktPsiFactory.createFile(readText())
    return ParsedSourceFile(
        packageName = ktFile.packageFqName.asString().takeIf { it.isNotEmpty() },
        imports = ktFile.importDirectives.mapNotNull { directive ->
            val path = directive.importPath?.pathStr ?: return@mapNotNull null
            ParsedImport(
                importPath = path,
                lineNumber = ktFile.viewProvider.document.getLineNumber(directive.textRange.startOffset) + 1,
                className = directive.importPath?.fqName?.shortName()?.asString()
            )
        }
    )
}

private val PACKAGE_PATTERN = Regex("""^\s*package\s+([\w.]+)\s*;""")
private val IMPORT_PATTERN = Regex("""^\s*import\s+(?:static\s+)?([\w.*]+)\s*;""")

private fun File.parseJavaSourceFile(): ParsedSourceFile {
    val lines = readLines()
    var packageName: String? = null
    val imports = mutableListOf<ParsedImport>()

    lines.forEachIndexed { index, line ->
        if (packageName == null) {
            PACKAGE_PATTERN.find(line)?.let {
                packageName = it.groupValues[1]
            }
        }
        IMPORT_PATTERN.find(line)?.let { match ->
            val importPath = match.groupValues[1]
            imports.add(
                ParsedImport(
                    importPath = importPath,
                    lineNumber = index + 1,
                    className = importPath.split(".").last().takeIf { it != "*" }
                )
            )
        }
    }

    return ParsedSourceFile(
        packageName = packageName?.takeIf { it.isNotEmpty() },
        imports = imports
    )
}

internal fun extractPackageFromImport(importPath: String): String {
    val parts = importPath.split(".")
    return parts.dropLast(1).joinToString(".")
}

internal fun getIgnoredViolationsFromBaseline(baselinePath: String): Map<String, List<ViolationData>> {
    val file = File(baselinePath)
    if (!file.exists()) return emptyMap()

    val document: Document =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)

    document.documentElement.normalize()

    val violationsNodeList: NodeList = document.getElementsByTagName("ID")

    val violations = mutableMapOf<String, MutableList<ViolationData>>()
    for (i in 0 until violationsNodeList.length) {
        val node = violationsNodeList.item(i)
        if (node is Element) {
            val idParts = node.textContent.trim().split("$")
            val violation =
                when (idParts.first()) {
                    "FileOnSameLevelAsPackages" ->
                        ViolationData.FileOnSameLevelAsPackages(
                            lineNumber = idParts[2].toInt(),
                            className = idParts[3],
                            importedPackage = idParts[4]
                        )

                    "ForbiddenImport" ->
                        ViolationData.ForbiddenImport(
                            lineNumber = idParts[2].toInt(),
                            importingPackage = idParts[3],
                            importedPackage = idParts[4]
                        )

                    else -> null
                }
            violation?.let { violations.computeIfAbsent(idParts[1]) { mutableListOf() } += it }
        }
    }
    return violations
}