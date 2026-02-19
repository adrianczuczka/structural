package com.adrianczuczka.structural

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.workers.WorkParameters
import java.io.File

internal interface StructuralParams : WorkParameters {
    val mode: Property<String>
    val sourceFiles: SetProperty<File>
    val rulesPath: Property<String>
    val baselinePath: Property<String>
}