package com.adrianczuczka.structural

import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory

object PsiFactoryProvider {

    private val disposable: Disposable = Disposer.newDisposable()

    private val environment: KotlinCoreEnvironment by lazy {
        KotlinCoreEnvironment.createForProduction(
            projectDisposable = disposable,
            configuration = CompilerConfiguration(),
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    val ktPsiFactory: KtPsiFactory = KtPsiFactory(project = environment.project)
}
