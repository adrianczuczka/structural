package com.adrianczuczka.structural

import org.jetbrains.kotlin.com.intellij.psi.PsiImportStaticStatement
import org.jetbrains.kotlin.psi.KtClassOrObject
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
    val imports: List<ParsedImport>,
    val topLevelClassNames: List<String>,
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
                    ?.takeUnless { directive.isAllUnder }
            )
        },
        topLevelClassNames = ktFile.declarations.filterIsInstance<KtClassOrObject>().mapNotNull { it.name },
    )
}

private fun File.parseJavaSourceFile(): ParsedSourceFile {
    val javaFile = PsiFactoryProvider.createJavaFile(name, readText())
    return ParsedSourceFile(
        packageName = javaFile.packageName.takeIf { it.isNotEmpty() },
        imports = javaFile.importList?.allImportStatements.orEmpty().mapNotNull { statement ->
            val reference = statement.importReference ?: return@mapNotNull null
            val importPath = reference.qualifiedName + if (statement.isOnDemand) ".*" else ""
            ParsedImport(
                importPath = importPath,
                lineNumber = javaFile.viewProvider.document!!.getLineNumber(statement.textRange.startOffset) + 1,
                className = importPath.substringAfterLast('.').takeUnless { statement.isOnDemand },
                isStatic = statement is PsiImportStaticStatement,
            )
        },
        topLevelClassNames = javaFile.classes.mapNotNull { it.name },
    )
}

/**
 * Resolve nested types and members using declarations in the checked sources.
 * A class name is not a package segment, regardless of capitalization or file
 * name. Unavailable types retain the existing syntactic import fallback; this
 * checker does not resolve the project's dependency classpath.
 */
internal class SourcePackageIndex(sources: Collection<ParsedSourceFile>) {
    private val packagesByType = buildMap {
        sources.forEach { source ->
            val pkg = source.packageName ?: return@forEach
            source.topLevelClassNames.forEach { name -> put("$pkg.$name", pkg) }
        }
    }

    fun importedPackage(import: ParsedImport): String {
        var prefix = import.importPath
        while ('.' in prefix) {
            packagesByType[prefix]?.let { return it }
            prefix = prefix.substringBeforeLast('.')
        }
        return extractPackageFromImport(import.importPath, import.isStatic)
    }
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
