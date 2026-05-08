package com.adrianczuczka.structural

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject


internal abstract class StructuralCheckTask : DefaultTask() {
    @get:Inject
    abstract val executor: WorkerExecutor

    @get:Classpath
    abstract val kotlinCompiler: ConfigurableFileCollection

    @get:InputFiles
    abstract val sourceFiles: ListProperty<File>

    @get:Input
    abstract val rulesPath: Property<String>

    @get:Input
    abstract val baselinePath: Property<String>

    @TaskAction
    fun run() {
        val workQueue = executor.classLoaderIsolation {
            classpath.from(kotlinCompiler)
        }
        workQueue.submit(StructuralWorkAction::class.java) {
            mode.set("check")
            sourceFiles.set(this@StructuralCheckTask.sourceFiles)
            rulesPath.set(this@StructuralCheckTask.rulesPath)
            baselinePath.set(this@StructuralCheckTask.baselinePath)
            findingsPath.set("")
        }
    }
}

internal abstract class StructuralBaselineTask : DefaultTask() {
    @get:Inject
    abstract val executor: WorkerExecutor

    @get:Classpath
    abstract val kotlinCompiler: ConfigurableFileCollection

    @get:InputFiles
    abstract val sourceFiles: ListProperty<File>

    @get:Input
    abstract val rulesPath: Property<String>

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
            sourceFiles.set(this@StructuralBaselineTask.sourceFiles)
            rulesPath.set(this@StructuralBaselineTask.rulesPath)
            baselinePath.set(this@StructuralBaselineTask.baselinePath)
            findingsPath.set(findingsAbsolute)
        }
    }
}
