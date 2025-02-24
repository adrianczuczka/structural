package com.adrianczuczka.structural

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import java.io.File

fun File.parseKotlinFile(): KtFile =
    PsiFactoryProvider.ktPsiFactory.createFile(readText())

fun KtFile.extractPackageName(): String? =
    packageFqName.asString().takeIf { it.isNotEmpty() }

fun extractPackageFromImport(importPath: String): String {
    val parts = importPath.split(".")
    return parts.dropLast(1).joinToString(".")
}

fun KtImportDirective.getLineNumber(): Int =
    containingKtFile.viewProvider.document.getLineNumber(textRange.startOffset) + 1