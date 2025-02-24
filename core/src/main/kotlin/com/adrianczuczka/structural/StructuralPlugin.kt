package com.adrianczuczka.structural

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

            doLast {
                checkForViolations(
                    kotlinFiles = kotlinFiles,
                    rulesPath = extension.config ?: defaultRulesPath,
                )
            }
        }
    }
}

private fun checkForViolations(
    kotlinFiles: Set<File>,
    rulesPath: String,
) {
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
                                        val importedLocalPackage =
                                            importedPackageParts
                                                .drop(packagePathParts.size)
                                                .first()
                                        val allowedList = rules[localPackageName] ?: emptyList()

                                        if (importedLocalPackage !in allowedList) {
                                            val errorMessage =
                                                "🚨 ${file.absolutePath}:${importDirective.getLineNumber()} : `$packageName` cannot import from `$importedPackage`"
                                            violations.computeIfAbsent(file) { mutableListOf() }
                                                .add(errorMessage)
                                        }
                                    }
                                }
                            }
                    }
                } else {
                    throw Exception("Could not extract package name from kotlin file")
                }
            }

            if (violations.isNotEmpty()) {
                println("🚨 Import rule violations found:")
                violations.values.flatten().forEach { value ->
                    println(value)
                }
                throw RuntimeException(
                    "Import rule violations detected in ${violations.size} ${
                        if (violations.size == 1) {
                            "file"
                        } else {
                            "files"
                        }
                    }."
                )
            } else {
                println("✅ All package imports follow the specified package rules.")
            }
        } else {
            throw Exception("Could not parse config file")
        }
    } else {
        throw Exception("Could not find config file")
    }
}

open class StructuralExtension {
    var config: String? = null
}