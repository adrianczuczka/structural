package com.adrianczuczka.structural

import com.adrianczuczka.structural.yaml.ClassRule
import java.io.File

/**
 * Returns true if any class rule grants the given import for the given source
 * file. Class rules are purely additive: they can flip a package-rule denial
 * into an allowance, but never the other way around.
 *
 * The importing class's identity is the file's `nameWithoutExtension` —
 * structural is file-scoped and does not parse top-level declarations beyond
 * the file name. This is why class rules referencing `api.ApiBuilder` only
 * fire when the source file is named `ApiBuilder.{kt,java}`.
 *
 * Wildcard imports (`import com.foo.*`) cannot be matched by class rules
 * because there is no class name to compare against; callers should still
 * apply the package-level decision in that case.
 */
internal fun isClassRuleGranted(
    file: File,
    importerPackage: String,
    import: ParsedImport,
    importedPackage: String,
    classRules: List<ClassRule>,
): Boolean {
    if (classRules.isEmpty()) return false
    val importedClassName = extractEnclosingClassFromImport(
        importPath = import.importPath,
        className = import.className,
        isStatic = import.isStatic,
    ) ?: return false
    val importerClassName = file.nameWithoutExtension
    return classRules.any { rule ->
        rule.matches(
            importerPackage = importerPackage,
            importerClassName = importerClassName,
            importedPackage = importedPackage,
            importedClassName = importedClassName,
        )
    }
}
