package com.adrianczuczka.structural

import org.gradle.api.Plugin
import org.gradle.api.Project

class StructuralPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension: StructuralExtension =
            project.extensions.create("structural", StructuralExtension::class.java)

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

        val defaultRulesPath = "${project.projectDir}/structural.yml"
        val defaultBaselinePath = "${project.projectDir}/baseline.xml"
        val rulesPathProvider = project.provider {
            extension.config?.let { project.file(it).absolutePath } ?: defaultRulesPath
        }
        val baselinePathProvider = project.provider {
            extension.baseline?.let { project.file(it).absolutePath } ?: defaultBaselinePath
        }

        project.tasks.register("structuralCheck", StructuralCheckTask::class.java) {
            group = "verification"
            description = "Checks if packages satisfy specified architecture"
            sourceFiles.set(sourceFilesProvider)
            rulesPath.set(rulesPathProvider)
            baselinePath.set(baselinePathProvider)
            kotlinCompiler.from(structuralConfiguration)
        }

        val baselineTask = project.tasks.register(
            "structuralGenerateBaseline",
            StructuralBaselineTask::class.java
        ) {
            group = "verification"
            description = "Generates baseline of package issues for this module"
            sourceFiles.set(sourceFilesProvider)
            rulesPath.set(rulesPathProvider)
            baselinePath.set(baselinePathProvider)
            findingsFile.set(project.layout.buildDirectory.file("structural/findings.txt"))
            kotlinCompiler.from(structuralConfiguration)
        }

        // Always register the root aggregator (idempotent across modules), but it only runs
        // when explicitly invoked via `./gradlew structuralAggregateBaseline`. Use this for
        // shared-baseline workflows where multiple modules point at one baseline file.
        val rootTasks = project.rootProject.tasks
        val aggregator = if (rootTasks.findByName("structuralAggregateBaseline") == null) {
            rootTasks.register("structuralAggregateBaseline", AggregateBaselineTask::class.java) {
                group = "verification"
                description = "Aggregates per-module findings into one baseline file per configured path"
            }
        } else {
            rootTasks.named("structuralAggregateBaseline", AggregateBaselineTask::class.java)
        }
        aggregator.configure {
            findingsFiles.from(baselineTask.flatMap { it.findingsFile })
            dependsOn(baselineTask)
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
