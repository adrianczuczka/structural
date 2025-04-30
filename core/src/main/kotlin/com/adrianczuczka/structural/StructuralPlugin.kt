package com.adrianczuczka.structural

import com.adrianczuczka.structural.baseline.BaselineData
import com.adrianczuczka.structural.baseline.toXml
import com.adrianczuczka.structural.yaml.parseYamlImportRules
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class StructuralPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension: StructuralExtension =
            project.extensions.create("structural", StructuralExtension::class.java)

        project.tasks.register("structuralCheck") {
            group = "verification"
            description = "Checks if packages satisfy specified architecture"

            val kotlinFiles = project.fileTree(project.projectDir) {
                include("**/src/main/kotlin/**/*.kt", "**/src/main/java/**/*.kt")
            }.files

            val defaultRulesPath = "${project.rootDir}/structural.yml"
            val defaultBaselinePath = "${project.rootDir}/baseline.xml"

            doLast {
                val violations = checkForViolations(
                    kotlinFiles = kotlinFiles,
                    rulesPath = extension.config ?: defaultRulesPath,
                    ignoredViolations = getIgnoredViolationsFromBaseline(
                        baselinePath = extension.baseline ?: defaultBaselinePath
                    )
                )
                violations.report()
            }
        }

        project.tasks.register("structuralGenerateBaseline") {
            group = "verification"
            description = "Generates baseline of package issues"

            val kotlinFiles = project.fileTree(project.projectDir) {
                include("**/src/main/kotlin/**/*.kt", "**/src/main/java/**/*.kt")
            }.files

            val defaultRulesPath = "${project.rootDir}/structural.yml"
            val defaultBaselinePath = "${project.rootDir}/baseline.xml"

            doLast {
                val violations = checkForViolations(
                    kotlinFiles = kotlinFiles,
                    rulesPath = extension.config ?: defaultRulesPath,
                    ignoredViolations = emptyMap()
                )
                violations.generateBaseline(extension.baseline ?: defaultBaselinePath)
            }
        }
    }
}

private fun checkForViolations(
    kotlinFiles: Set<File>,
    rulesPath: String,
    ignoredViolations: Map<String, List<ViolationData>>
): Map<File, List<ViolationData>> {
    val rulesFile = File(rulesPath)
    if (rulesFile.exists()) {
        val yaml = rulesFile.parseYamlImportRules()
        if (yaml != null) {
            val (checkedPackages, rules) = yaml

            println("📜 Allowed import rules loaded: $rules")

            val violations = mutableMapOf<File, MutableList<ViolationData>>()

            kotlinFiles.forEach { file ->
                val ktFile = file.parseKotlinFile()
                val packageName = ktFile.extractPackageName()

                if (packageName != null) {
                    /*
                        If file's package is com.example.test.domain, this will be
                        "domain"
                     */
                    val localPackageName = packageName.split(".").last()
                    /*
                        If file's package is com.example.test.domain, this will be
                        "com.example.test"
                     */
                    val packagePathParts =
                        packageName
                            .split(".")
                            .dropLast(1)

                    if (localPackageName in checkedPackages) {
                        // Check each import in file for violations
                        ktFile.importDirectives
                            .forEach { importDirective ->
                                val importPath = importDirective.importPath?.pathStr
                                if (importPath != null) {
                                    val importedPackage = extractPackageFromImport(importPath)
                                    val importedPackageParts = importedPackage.split(".")
                                    if (
                                        importedPackageParts.take(packagePathParts.size) == packagePathParts
                                    ) {
                                        // This means there's a file on the same level as the packages being checked. Disallow
                                        if (importedPackageParts.size == packagePathParts.size) {
                                            importDirective
                                                .importPath
                                                ?.fqName
                                                ?.shortName()
                                                ?.asString()
                                                ?.let { className ->
                                                    val violationData =
                                                        ViolationData.FileOnSameLevelAsPackages(
                                                            lineNumber = importDirective.getLineNumber(),
                                                            className = className,
                                                            importedPackage = importedPackage
                                                        )
                                                    if (
                                                        violationData !in ignoredViolations[file.nameWithoutExtension].orEmpty()
                                                    ) {
                                                        violations.computeIfAbsent(file) { mutableListOf() }
                                                            .add(violationData)
                                                    }
                                                }
                                        } else {
                                            val importedLocalPackage =
                                                importedPackageParts
                                                    .drop(packagePathParts.size)
                                                    .first()
                                            val allowedList = rules[localPackageName] ?: emptyList()

                                            val violationData = ViolationData.ForbiddenImport(
                                                lineNumber = importDirective.getLineNumber(),
                                                importingPackage = packageName,
                                                importedPackage = importedPackage
                                            )

                                            if (
                                                importedLocalPackage !in allowedList &&
                                                violationData !in ignoredViolations[file.nameWithoutExtension].orEmpty()
                                            ) {
                                                violations.computeIfAbsent(file) { mutableListOf() }.add(violationData)
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
            return violations
        } else {
            throw Exception("Could not parse config file")
        }
    } else {
        println(rulesPath)
        throw Exception("Could not find config file")
    }
}

private fun Map<File, List<ViolationData>>.report() {
    if (isNotEmpty()) {
        println("\uD83D\uDEA8 Import rule violations found:")
        entries.forEach { (file, violations) ->
            violations.forEach { violation ->
                val errorMessage =
                    when (violation) {
                        is ViolationData.FileOnSameLevelAsPackages ->
                            "${file.absolutePath}:${violation.lineNumber} : `class \"${violation.className}\" is on the same level as \"${violation.importedPackage}\" package. Move into a package`"

                        is ViolationData.ForbiddenImport ->
                            "${file.absolutePath}:${violation.lineNumber} : `${violation.importingPackage}` cannot import from `${violation.importedPackage}`"
                    }
                println("\uD83D\uDEA8 $errorMessage")
            }
        }
        throw RuntimeException(
            "Import rule violations detected in $size ${
                if (size == 1) {
                    "file"
                } else {
                    "files"
                }
            }."
        )
    } else {
        println("✅ All package imports follow the specified package rules.")
    }
}

internal sealed class ViolationData {
    data class FileOnSameLevelAsPackages(
        val lineNumber: Int,
        val className: String,
        val importedPackage: String
    ) : ViolationData()

    data class ForbiddenImport(
        val lineNumber: Int,
        val importingPackage: String,
        val importedPackage: String
    ) : ViolationData()
}

private fun Map<File, List<ViolationData>>.generateBaseline(baselinePath: String) {
    val baselineEntries =
        entries.map { (file, violations) ->
            violations.map { violation ->
                when (violation) {
                    is ViolationData.FileOnSameLevelAsPackages ->
                        "FileOnSameLevelAsPackages" +
                                "$${file.nameWithoutExtension}" +
                                "$${violation.lineNumber}" +
                                "$${violation.className}" +
                                "$${violation.importedPackage}"

                    is ViolationData.ForbiddenImport ->
                        "ForbiddenImport" +
                                "$${file.nameWithoutExtension}" +
                                "$${violation.lineNumber}" +
                                "$${violation.importingPackage}" +
                                "$${violation.importedPackage}"
                }
            }
        }.flatten()
    File(baselinePath).writeText(
        BaselineData(baselineEntries).toXml()
    )
}

open class StructuralExtension {
    var config: String? = null
    var baseline: String? = null
}