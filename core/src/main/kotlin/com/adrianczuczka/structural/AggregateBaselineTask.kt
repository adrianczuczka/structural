package com.adrianczuczka.structural

import com.adrianczuczka.structural.baseline.BaselineData
import com.adrianczuczka.structural.baseline.toXml
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

internal abstract class AggregateBaselineTask : DefaultTask() {

    @get:InputFiles
    abstract val findingsFiles: ConfigurableFileCollection

    @TaskAction
    fun aggregate() {
        val byBaseline = mutableMapOf<String, MutableSet<String>>()
        findingsFiles.forEach { file ->
            if (!file.exists()) return@forEach
            val lines = file.readLines()
            val baselinePath = lines.firstOrNull()
                ?.takeIf { it.startsWith("# baseline=") }
                ?.removePrefix("# baseline=")
                ?: return@forEach
            val ids = lines.drop(1).filter { it.isNotBlank() }
            byBaseline.getOrPut(baselinePath) { mutableSetOf() }.addAll(ids)
        }
        byBaseline.forEach { (baselinePath, ids) ->
            val baselineFile = File(baselinePath)
            baselineFile.parentFile?.mkdirs()
            baselineFile.writeText(BaselineData(ids.sorted()).toXml())
        }
    }
}
