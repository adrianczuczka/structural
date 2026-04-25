package com.adrianczuczka.structural

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

internal data class ParsedImport(
    val importPath: String,
    val lineNumber: Int,
    val className: String?,
    val isStatic: Boolean = false
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
private val IMPORT_PATTERN = Regex("""^\s*import\s+(static\s+)?([\w.*]+)\s*;""")

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
            val isStatic = match.groupValues[1].isNotBlank()
            val importPath = match.groupValues[2]
            imports.add(
                ParsedImport(
                    importPath = importPath,
                    lineNumber = index + 1,
                    className = importPath.split(".").last().takeIf { it != "*" },
                    isStatic = isStatic
                )
            )
        }
    }

    return ParsedSourceFile(
        packageName = packageName?.takeIf { it.isNotEmpty() },
        imports = imports
    )
}

internal fun extractPackageFromImport(importPath: String, isStatic: Boolean = false): String {
    val parts = importPath.split(".")
    if (parts.size <= 1) return importPath
    val dropCount = if (isStatic) 2 else 1
    return parts.dropLast(dropCount.coerceAtMost(parts.size - 1)).joinToString(".")
}

/**
 * The class name to match against class rules for a given import. For regular
 * imports this is the imported class itself; for Java static imports it's the
 * *enclosing* class (so `import static com.foo.Util.LOG` is matched as `Util`,
 * not `LOG`). Returns null for wildcard imports.
 */
internal fun extractEnclosingClassFromImport(
    importPath: String,
    className: String?,
    isStatic: Boolean,
): String? {
    if (className == null) return null
    if (!isStatic) return className
    val parts = importPath.split(".")
    return parts.getOrNull(parts.size - 2)
}

internal fun getIgnoredViolationsFromBaseline(baselinePath: String): Map<String, List<ViolationData>> {
    val file = File(baselinePath)
    if (!file.exists()) return emptyMap()

    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    val document: Document = factory.newDocumentBuilder().parse(file)

    document.documentElement.normalize()

    val violationsNodeList: NodeList = document.getElementsByTagName("ID")

    val violations = mutableMapOf<String, MutableList<ViolationData>>()
    for (i in 0 until violationsNodeList.length) {
        val node = violationsNodeList.item(i)
        if (node is Element) {
            val idParts = node.textContent.trim().split("$")
            if (idParts.size < 4) continue
            val violation =
                when (idParts.first()) {
                    "FileOnSameLevelAsPackages" ->
                        ViolationData.FileOnSameLevelAsPackages(
                            className = idParts[2],
                            importedPackage = idParts[3]
                        )

                    "ForbiddenImport" ->
                        ViolationData.ForbiddenImport(
                            importingPackage = idParts[2],
                            importPath = idParts[3]
                        )

                    else -> null
                }
            violation?.let { violations.computeIfAbsent(idParts[1]) { mutableListOf() } += it }
        }
    }
    return violations
}