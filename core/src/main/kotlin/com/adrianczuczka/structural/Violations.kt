package com.adrianczuczka.structural

internal data class ReportedViolation(
    val violation: ViolationData,
    val lineNumber: Int,
    val importedPackage: String,
)

internal sealed class ViolationData {
    data class FileOnSameLevelAsPackages(
        val className: String,
        val importedPackage: String,
    ) : ViolationData()

    data class ForbiddenImport(
        val importingPackage: String,
        val importPath: String,
    ) : ViolationData()
}
