package com.adrianczuczka.structural

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import java.io.File
import javax.inject.Inject

class StructuralPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension: StructuralExtension =
            project.extensions.create("structural", StructuralExtension::class.java)
        extension.config.convention(project.layout.projectDirectory.file("structural.yml"))
        extension.baseline.convention(project.layout.projectDirectory.file("baseline.xml"))

        val sourceProvider = project.provider { resolveSourceTree(project, extension) }

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

        project.tasks.register("structuralCheck", StructuralCheckTask::class.java) {
            group = "verification"
            description = "Checks if packages satisfy specified architecture"
            sourceFiles.from(sourceProvider)
            rulesFile.set(extension.config)
            baselineFile.set(extension.baseline)
            reportFile.set(project.layout.buildDirectory.file("reports/structural/check.txt"))
            kotlinCompiler.from(structuralConfiguration)
        }

        val baselineTask = project.tasks.register(
            "structuralGenerateBaseline",
            StructuralBaselineTask::class.java
        ) {
            group = "verification"
            description = "Generates baseline of package issues for this module"
            sourceFiles.from(sourceProvider)
            rulesFile.set(extension.config)
            baselinePath.set(extension.baseline.map { it.asFile.absolutePath })
            findingsFile.set(project.layout.buildDirectory.file("structural/findings.txt"))
            kotlinCompiler.from(structuralConfiguration)
        }

        // Expose the findings file as an outgoing variant so an aggregating project can
        // consume it through dependency resolution instead of reaching into this project.
        val findingsElements = project.configurations.create("structuralFindingsElements") {
            isCanBeConsumed = true
            isCanBeResolved = false
            attributes.attribute(
                STRUCTURAL_CATEGORY_ATTRIBUTE,
                project.objects.named(Category::class.java, STRUCTURAL_FINDINGS_CATEGORY)
            )
        }
        project.artifacts.add(findingsElements.name, baselineTask.flatMap { it.findingsFile })
    }

    companion object {
        const val KOTLIN_COMPILER_EMBEDDABLE = "org.jetbrains.kotlin:kotlin-compiler-embeddable"
        const val KOTLIN_COMPILER_VERSION = "2.1.0"
    }
}

open class StructuralExtension @Inject constructor(objects: ObjectFactory) {
    /** Path to the rules file. Defaults to `structural.yml` in the project directory. */
    val config: RegularFileProperty = objects.fileProperty()

    /** Path to the baseline file. Defaults to `baseline.xml` in the project directory. */
    val baseline: RegularFileProperty = objects.fileProperty()

    /**
     * Explicit source files or directories to check. When set, this wins over both
     * source-set discovery and the glob fallback.
     */
    val source: ConfigurableFileCollection = objects.fileCollection()
}

private val SOURCE_EXTENSIONS = listOf("**/*.kt", "**/*.java")

/**
 * Source discovery, in priority order: the extension's explicit `source` collection,
 * the `main` source set when a JVM plugin is applied, and otherwise a glob over the
 * project directory. Evaluated lazily so plugin application order doesn't matter.
 */
private fun resolveSourceTree(project: Project, extension: StructuralExtension): FileTree {
    if (!extension.source.isEmpty) {
        return extension.source.asFileTree.matching { include(SOURCE_EXTENSIONS) }
    }
    return mainSourceSetTree(project) ?: globSourceTree(project)
}

private fun mainSourceSetTree(project: Project): FileTree? {
    val sourceSets = project.extensions.findByType(SourceSetContainer::class.java) ?: return null
    val main = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME) ?: return null
    val dirs = project.objects.fileCollection()
    dirs.from(main.allJava.sourceDirectories)
    val kotlinExtension = (main as ExtensionAware).extensions.findByName("kotlin")
    if (kotlinExtension is SourceDirectorySet) {
        dirs.from(kotlinExtension.sourceDirectories)
    }
    return dirs.asFileTree.matching { include(SOURCE_EXTENSIONS) }
}

private fun globSourceTree(project: Project): FileTree {
    val projectDir = project.projectDir
    val buildDir = project.layout.buildDirectory.get().asFile
    return project.fileTree(projectDir) {
        include(
            "**/src/main/kotlin/**/*.kt",
            "**/src/main/kotlin/**/*.java",
            "**/src/main/java/**/*.kt",
            "**/src/main/java/**/*.java",
        )
        // Prune build outputs, and nested checkouts (e.g. a git worktree added inside
        // the project – a .git entry below the project root): their sources belong to
        // generated output or another working copy, not to this project.
        exclude { element ->
            element.file.isDirectory &&
                    (element.file == buildDir ||
                            (element.file != projectDir && File(element.file, ".git").exists()))
        }
    }
}
