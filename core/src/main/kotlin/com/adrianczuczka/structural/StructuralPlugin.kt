package com.adrianczuczka.structural

import org.gradle.api.Plugin
import org.gradle.api.Project

class StructuralPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension: StructuralExtension =
            project.extensions.create("structural", StructuralExtension::class.java)

        if (project.repositories.isEmpty()) {
            project.repositories.mavenCentral()
        }

        val sourceFilesProvider = project.provider {
            project.fileTree(project.projectDir) {
                include("**/src/main/kotlin/**/*.kt", "**/src/main/kotlin/**/*.java", "**/src/main/java/**/*.kt", "**/src/main/java/**/*.java")
            }.files.toList()
        }

        val structuralScope = project.configurations.create("structuralScope") {
            isCanBeResolved = false
            isCanBeConsumed = false
        }
        project.dependencies.add(
            structuralScope.name,
            "$KOTLIN_COMPILER_EMBEDDABLE:$KOTLIN_COMPILER_VERSION"
        )
        val structuralConfiguration = project.configurations.create("structuralConfig") {
            extendsFrom(structuralScope)
            isCanBeResolved = true
            isCanBeConsumed = false
        }

        project.tasks.register("structuralCheck", StructuralTask::class.java) {
            group = "verification"
            description = "Checks if packages satisfy specified architecture"

            val defaultRulesPath = "${project.rootDir}/structural.yml"
            val defaultBaselinePath = "${project.rootDir}/baseline.xml"

            mode.set("check")
            sourceFiles.set(sourceFilesProvider)
            rulesPath.set(extension.config ?: defaultRulesPath)
            baselinePath.set(extension.baseline ?: defaultBaselinePath)
            kotlinCompiler.from(structuralConfiguration)
        }

        project.tasks.register("structuralGenerateBaseline", StructuralTask::class.java) {
            group = "verification"
            description = "Generates baseline of package issues"

            val defaultRulesPath = "${project.rootDir}/structural.yml"
            val defaultBaselinePath = "${project.rootDir}/baseline.xml"

            mode.set("baseline")
            sourceFiles.set(sourceFilesProvider)
            rulesPath.set(extension.config ?: defaultRulesPath)
            baselinePath.set(extension.baseline ?: defaultBaselinePath)
            kotlinCompiler.from(structuralConfiguration)
        }
    }

    companion object {
        const val KOTLIN_COMPILER_EMBEDDABLE = "org.jetbrains.kotlin:kotlin-compiler-embeddable"
        const val KOTLIN_COMPILER_VERSION = "2.1.0"
    }
}

open class StructuralExtension {
    var config: String? = null
    var baseline: String? = null
}