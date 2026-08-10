package com.adrianczuczka.structural

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject


@CacheableTask
internal abstract class StructuralCheckTask : DefaultTask() {
    @get:Inject
    abstract val executor: WorkerExecutor

    @get:Classpath
    abstract val kotlinCompiler: ConfigurableFileCollection

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    // @InputFiles (not @InputFile) so a missing rules file reaches the work action,
    // which fails with the plugin's own error instead of Gradle's validation message.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rulesFile: RegularFileProperty

    // The baseline is optional – @InputFiles tolerates its absence while still
    // invalidating the task when its content changes.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun run() {
        val workQueue = executor.classLoaderIsolation {
            classpath.from(kotlinCompiler)
        }
        workQueue.submit(StructuralWorkAction::class.java) {
            mode.set("check")
            sourceFiles.set(this@StructuralCheckTask.sourceFiles.files)
            rulesPath.set(this@StructuralCheckTask.rulesFile.get().asFile.absolutePath)
            baselinePath.set(this@StructuralCheckTask.baselineFile.get().asFile.absolutePath)
            findingsPath.set("")
        }
        workQueue.await()
        val report = reportFile.get().asFile
        report.parentFile?.mkdirs()
        report.writeText("Structural check passed: ${sourceFiles.files.size} source file(s) checked.\n")
    }
}

internal abstract class StructuralBaselineTask : DefaultTask() {
    @get:Inject
    abstract val executor: WorkerExecutor

    @get:Classpath
    abstract val kotlinCompiler: ConfigurableFileCollection

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rulesFile: RegularFileProperty

    // The baseline file is written by the work action and may be shared between
    // modules, so it's deliberately not declared as an output: overlapping outputs
    // would break up-to-date checks for every sharing module. The path is an input
    // because the findings header embeds it.
    @get:Input
    abstract val baselinePath: Property<String>

    @get:OutputFile
    abstract val findingsFile: RegularFileProperty

    @TaskAction
    fun run() {
        val workQueue = executor.classLoaderIsolation {
            classpath.from(kotlinCompiler)
        }
        val findingsAbsolute = findingsFile.get().asFile.absolutePath
        workQueue.submit(StructuralWorkAction::class.java) {
            mode.set("findings")
            sourceFiles.set(this@StructuralBaselineTask.sourceFiles.files)
            rulesPath.set(this@StructuralBaselineTask.rulesFile.get().asFile.absolutePath)
            baselinePath.set(this@StructuralBaselineTask.baselinePath)
            findingsPath.set(findingsAbsolute)
        }
    }
}
