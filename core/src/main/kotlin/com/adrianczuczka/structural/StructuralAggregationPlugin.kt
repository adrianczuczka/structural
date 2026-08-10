package com.adrianczuczka.structural

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category

internal val STRUCTURAL_CATEGORY_ATTRIBUTE = Category.CATEGORY_ATTRIBUTE
internal const val STRUCTURAL_FINDINGS_CATEGORY = "structural-findings"

/**
 * Root-side aggregation. Applied to a parent project (typically the root), it collects
 * every module's findings file through dependency resolution and merges them into the
 * configured baseline file(s). Modules are addressed by path only and never configured
 * from here, so this stays compatible with Gradle's Isolated Projects mode.
 */
class StructuralAggregationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val scope = project.configurations.create("structuralAggregation") {
            isCanBeConsumed = false
            isCanBeResolved = false
            description = "Projects whose structural findings are aggregated into a shared baseline"
        }

        // Default to every project in this build. Projects that don't apply the
        // structural plugin expose no matching variant and are dropped by the
        // lenient artifact view below.
        val allProjectPaths = listOf(project.path) + project.subprojects.map { it.path }
        allProjectPaths.forEach { path ->
            scope.dependencies.add(project.dependencies.project(mapOf("path" to path)))
        }

        val findings = project.configurations.create("structuralAggregationFindings") {
            extendsFrom(scope)
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes.attribute(
                STRUCTURAL_CATEGORY_ATTRIBUTE,
                project.objects.named(Category::class.java, STRUCTURAL_FINDINGS_CATEGORY)
            )
        }

        project.tasks.register("structuralAggregateBaseline", AggregateBaselineTask::class.java) {
            group = "verification"
            description = "Aggregates per-module findings into one baseline file per configured path"
            findingsFiles.from(findings.incoming.artifactView { isLenient = true }.files)
        }
    }
}
