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
                    ignoredViolations = emptySet()
                )
                violations.generateBaseline(extension.baseline ?: defaultBaselinePath)
            }
        }
    }
}

private fun checkForViolations(
    kotlinFiles: Set<File>,
    rulesPath: String,
    ignoredViolations: Set<String>
): Map<File, List<String>> {
    val rulesFile = File(rulesPath)
    if (rulesFile.exists()) {
        val yaml = rulesFile.parseYamlImportRules()
        if (yaml != null) {
            val (checkedPackages, rules) = yaml

            println("📜 Allowed import rules loaded: $rules")

            val violations = mutableMapOf<File, MutableList<String>>()

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
                                            val className = importDirective.importPath?.fqName?.shortName()?.asString()
                                            val errorMessage =
                                                "${file.absolutePath}:${importDirective.getLineNumber()} : `class \"$className\" is on the same level as \"$packageName\" package. Move into a package`"
                                            if (errorMessage !in ignoredViolations) {
                                                violations.computeIfAbsent(file) { mutableListOf() }.add(errorMessage)
                                            }
                                        } else {
                                            val importedLocalPackage =
                                                importedPackageParts
                                                    .drop(packagePathParts.size)
                                                    .first()
                                            val allowedList = rules[localPackageName] ?: emptyList()

                                            val errorMessage =
                                                "${file.absolutePath}:${importDirective.getLineNumber()} : `$packageName` cannot import from `$importedPackage`"

                                            if (importedLocalPackage !in allowedList && errorMessage !in ignoredViolations) {
                                                violations.computeIfAbsent(file) { mutableListOf() }.add(errorMessage)
                                            }
                                        }
                                    }
                                }
                            }
                    }
                } else {
                    throw Exception("Could not extract package name from kotlin file")
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

private fun Map<File, List<String>>.report() {
    if (isNotEmpty()) {
        println("\uD83D\uDEA8 Import rule violations found:")
        values.flatten().forEach { value ->
            println("\uD83D\uDEA8 $value")
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

private fun Map<File, List<String>>.generateBaseline(baselinePath: String) {
    File(baselinePath).writeText(BaselineData(values.flatten()).toXml())
}

open class StructuralExtension {
    var config: String? = null
    var baseline: String? = null
}