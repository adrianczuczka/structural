package com.adrianczuczka.structural

import com.adrianczuczka.structural.baseline.BaselineData
import com.adrianczuczka.structural.baseline.toXml
import com.adrianczuczka.structural.yaml.ClassRule
import com.adrianczuczka.structural.yaml.TrackedPackage
import com.adrianczuczka.structural.yaml.parseYamlImportRules
import com.adrianczuczka.structural.yaml.specificity
import org.gradle.api.GradleException
import org.gradle.workers.WorkAction
import java.io.File

abstract class StructuralWorkAction : WorkAction<StructuralParams> {
    override fun execute() {
        val mode = parameters.mode.get()
        val rulesPath = parameters.rulesPath.get()
        val baselinePath = parameters.baselinePath.get()
        val files = parameters.sourceFiles.get()

        if (mode == "check") {
            val violations = checkForViolations(
                sourceFiles = files,
                rulesPath = rulesPath,
                ignoredViolations = getIgnoredViolationsFromBaseline(baselinePath)
            )
            violations.report()
        } else {
            val violations = checkForViolations(
                sourceFiles = files,
                rulesPath = rulesPath,
                ignoredViolations = emptyMap()
            )
            violations.generateBaseline(baselinePath)
        }
    }
}

private fun checkForViolations(
    sourceFiles: Set<File>,
    rulesPath: String,
    ignoredViolations: Map<String, List<ViolationData>>
): Map<File, List<ReportedViolation>> {
    val rulesFile = File(rulesPath)
    if (!rulesFile.exists()) {
        throw GradleException("Could not find config file")
    }
    val yaml = rulesFile.parseYamlImportRules()
        ?: throw GradleException("Could not parse config file")

    val (checkedPackages, rules, classRules) = yaml

    println("📜 Allowed import rules loaded: $rules")
    if (classRules.isNotEmpty()) {
        println("📜 Class rules loaded: ${classRules.size} rule(s)")
    }

    val violations = mutableMapOf<File, MutableList<ReportedViolation>>()

    val singleSegmentPackages =
        checkedPackages.filter { it.isSingleSegment }.map { it.pattern }.toSet()
    // Most-specific first so literal tracked packages win over wildcards.
    val multiSegmentTracked =
        checkedPackages.filter { !it.isSingleSegment }.sortedByDescending { it.specificity() }

    sourceFiles.forEach { file ->
        val sourceFile = file.parseSourceFile()
        val packageName = sourceFile.packageName ?: return@forEach
        val multiSegmentMatch = multiSegmentTracked.find { it.matches(packageName) }

        if (multiSegmentMatch != null) {
            sourceFile.imports.forEach { import ->
                violations.checkMultiSegmentImport(
                    file = file,
                    packageName = packageName,
                    import = import,
                    multiSegmentMatch = multiSegmentMatch,
                    multiSegmentTracked = multiSegmentTracked,
                    rules = rules,
                    classRules = classRules,
                    ignoredViolations = ignoredViolations,
                )
            }
        } else {
            val parts = packageName.split(".")
            parts.forEachIndexed { index, part ->
                if (part in singleSegmentPackages) {
                    val packagePathParts = parts.take(index)
                    sourceFile.imports.forEach { import ->
                        violations.checkSingleSegmentImport(
                            file = file,
                            packageName = packageName,
                            import = import,
                            trackedPart = part,
                            packagePathParts = packagePathParts,
                            rules = rules,
                            classRules = classRules,
                            ignoredViolations = ignoredViolations,
                        )
                    }
                }
            }
        }
    }
    return violations
}

private fun MutableMap<File, MutableList<ReportedViolation>>.checkMultiSegmentImport(
    file: File,
    packageName: String,
    import: ParsedImport,
    multiSegmentMatch: TrackedPackage,
    multiSegmentTracked: List<TrackedPackage>,
    rules: Map<TrackedPackage, List<TrackedPackage>>,
    classRules: List<ClassRule>,
    ignoredViolations: Map<String, List<ViolationData>>,
) {
    val importedPackage = extractPackageFromImport(import.importPath, import.isStatic)
    val importedTrackedPackage = multiSegmentTracked.find { it.matches(importedPackage) } ?: return
    if (importedTrackedPackage == multiSegmentMatch) return

    val allowedList = rules[multiSegmentMatch] ?: emptyList()
    if (importedTrackedPackage in allowedList) return

    if (isClassRuleGranted(file, packageName, import, importedPackage, classRules)) return

    recordIfNotIgnored(
        file = file,
        violationData = ViolationData.ForbiddenImport(
            importingPackage = packageName,
            importPath = import.importPath,
        ),
        lineNumber = import.lineNumber,
        importedPackage = importedPackage,
        ignoredViolations = ignoredViolations,
    )
}

private fun MutableMap<File, MutableList<ReportedViolation>>.checkSingleSegmentImport(
    file: File,
    packageName: String,
    import: ParsedImport,
    trackedPart: String,
    packagePathParts: List<String>,
    rules: Map<TrackedPackage, List<TrackedPackage>>,
    classRules: List<ClassRule>,
    ignoredViolations: Map<String, List<ViolationData>>,
) {
    val importedPackage = extractPackageFromImport(import.importPath, import.isStatic)
    val importedPackageParts = importedPackage.split(".")
    if (importedPackageParts.take(packagePathParts.size) != packagePathParts) return

    if (importedPackageParts.size == packagePathParts.size) {
        val className = import.className ?: return
        recordIfNotIgnored(
            file = file,
            violationData = ViolationData.FileOnSameLevelAsPackages(
                className = className,
                importedPackage = importedPackage,
            ),
            lineNumber = import.lineNumber,
            importedPackage = importedPackage,
            ignoredViolations = ignoredViolations,
        )
        return
    }

    val importedLocalPackage = importedPackageParts.drop(packagePathParts.size).first()
    if (importedLocalPackage == trackedPart) return

    val allowedPatterns = (rules[TrackedPackage(trackedPart)] ?: emptyList()).map { it.pattern }
    if (importedLocalPackage in allowedPatterns) return

    if (isClassRuleGranted(file, packageName, import, importedPackage, classRules)) return

    recordIfNotIgnored(
        file = file,
        violationData = ViolationData.ForbiddenImport(
            importingPackage = packageName,
            importPath = import.importPath,
        ),
        lineNumber = import.lineNumber,
        importedPackage = importedPackage,
        ignoredViolations = ignoredViolations,
    )
}

private fun MutableMap<File, MutableList<ReportedViolation>>.recordIfNotIgnored(
    file: File,
    violationData: ViolationData,
    lineNumber: Int,
    importedPackage: String,
    ignoredViolations: Map<String, List<ViolationData>>,
) {
    if (violationData in ignoredViolations[file.nameWithoutExtension].orEmpty()) return
    computeIfAbsent(file) { mutableListOf() }
        .add(ReportedViolation(violationData, lineNumber, importedPackage))
}

private fun Map<File, List<ReportedViolation>>.report() {
    if (isNotEmpty()) {
        println("🚨 Import rule violations found:")
        entries.forEach { (file, reportedViolations) ->
            reportedViolations.forEach { (violation, lineNumber, importedPackage) ->
                val errorMessage =
                    when (violation) {
                        is ViolationData.FileOnSameLevelAsPackages ->
                            "${file.absolutePath}:$lineNumber : `class \"${violation.className}\" is on the same level as \"${violation.importedPackage}\" package. Move into a package`"

                        is ViolationData.ForbiddenImport ->
                            "${file.absolutePath}:$lineNumber : `${violation.importingPackage}` cannot import from `$importedPackage`"
                    }
                println("🚨 $errorMessage")
            }
        }
        val totalViolations = values.sumOf { it.size }
        throw GradleException(
            "$totalViolations import rule violation(s) detected in $size file(s)."
        )
    } else {
        println("✅ All package imports follow the specified package rules.")
    }
}

private fun Map<File, List<ReportedViolation>>.generateBaseline(baselinePath: String) {
    val baselineEntries =
        entries.flatMap { (file, reportedViolations) ->
            reportedViolations.map { (violation, _, _) ->
                when (violation) {
                    is ViolationData.FileOnSameLevelAsPackages ->
                        "FileOnSameLevelAsPackages" +
                                "$${file.nameWithoutExtension}" +
                                "$${violation.className}" +
                                "$${violation.importedPackage}"

                    is ViolationData.ForbiddenImport ->
                        "ForbiddenImport" +
                                "$${file.nameWithoutExtension}" +
                                "$${violation.importingPackage}" +
                                "$${violation.importPath}"
                }
            }
        }.distinct().sorted()
    File(baselinePath).writeText(
        BaselineData(baselineEntries).toXml()
    )
}
