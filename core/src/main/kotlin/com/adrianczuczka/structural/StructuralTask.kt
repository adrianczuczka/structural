package com.adrianczuczka.structural

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject


internal abstract class StructuralTask : DefaultTask() {
    @get:Inject
    abstract val executor: WorkerExecutor

    @get:Classpath
    abstract val kotlinCompiler: ConfigurableFileCollection

    @get:Input
    abstract val mode: Property<String> // "check" or "baseline"

    @get:InputFiles
    abstract val sourceFiles: ListProperty<File>

    @get:Input
    abstract val rulesPath: Property<String>

    @get:Input
    abstract val baselinePath: Property<String>

    @TaskAction
    fun compile() {
        val workQueue = executor.classLoaderIsolation {
            classpath.from(kotlinCompiler)
        }
        workQueue.submit(StructuralWorkAction::class.java) {
            mode.set(this@StructuralTask.mode)
            sourceFiles.set(this@StructuralTask.sourceFiles)
            rulesPath.set(this@StructuralTask.rulesPath)
            baselinePath.set(this@StructuralTask.baselinePath)
        }
    }
}