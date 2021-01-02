/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.console

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.KotlinIdeaReplBundle
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.artifacts.KotlinClassPath
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector
import org.jetbrains.kotlin.idea.util.JavaParametersBuilder
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.platform.subplatformsOfType
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class KotlinConsoleKeeper(val project: Project) {
    private val consoleMap: MutableMap<VirtualFile, KotlinConsoleRunner> = ConcurrentHashMap()

    fun getConsoleByVirtualFile(virtualFile: VirtualFile) = consoleMap[virtualFile]
    fun putVirtualFileToConsole(virtualFile: VirtualFile, console: KotlinConsoleRunner) = consoleMap.put(virtualFile, console)
    fun removeConsole(virtualFile: VirtualFile) = consoleMap.remove(virtualFile)

    fun run(module: Module, previousCompilationFailed: Boolean = false): KotlinConsoleRunner {
        val path = module.moduleFilePath
        val cmdLine = createReplCommandLine(project, module)
        val consoleRunner = KotlinConsoleRunner(
            module,
            cmdLine,
            previousCompilationFailed,
            project,
            KotlinIdeaReplBundle.message("name.kotlin.repl"),
            path
        )

        consoleRunner.initAndRun()
        return consoleRunner
    }

    companion object {
        private val LOG = Logger.getInstance("#org.jetbrains.kotlin.console")

        @JvmStatic
        fun getInstance(project: Project) = ServiceManager.getService(project, KotlinConsoleKeeper::class.java)

        fun createReplCommandLine(project: Project, module: Module?): GeneralCommandLine {
            val javaParameters = JavaParametersBuilder(project)
                .withSdkFrom(module, true)
                .withMainClassName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                .build()

            javaParameters.charset = null
            with(javaParameters.vmParametersList) {
                add("-Dkotlin.repl.ideMode=true")

                if (isUnitTestMode() && javaParameters.jdk?.version?.isAtLeast(JavaSdkVersion.JDK_1_9) == true) {
                    // TODO: Have to get rid of illegal access to java.util.ResourceBundle.setParent(java.util.ResourceBundle):
                    //  WARNING: Illegal reflective access by com.intellij.util.ReflectionUtil (file:...kotlin-ide/intellij/out/kotlinc-dist/kotlinc/lib/kotlin-compiler.jar) to method java.util.ResourceBundle.setParent(java.util.ResourceBundle)
                    //  WARNING: Please consider reporting this to the maintainers of com.intellij.util.ReflectionUtil
                    //  WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
                    //  WARNING: All illegal access operations will be denied in a future release
                    add("--add-opens")
                    add("java.base/java.util=ALL-UNNAMED")
                }
            }

            javaParameters.classPath.apply {
                val classPath = KotlinClassPath.CompilerWithScripting.computeClassPath()
                addAll(classPath.map {
                    val absolutePath = it.absolutePath
                    if (!it.exists()) {
                        LOG.warn("Compiler dependency classpath $absolutePath does not exist")
                    }
                    absolutePath
                })
            }

            if (module != null) {
                val classPath = JavaParametersBuilder.getModuleDependencies(module)
                if (classPath.isNotEmpty()) {
                    javaParameters.setUseDynamicParameters(javaParameters.isDynamicClasspath)
                    with(javaParameters.programParametersList) {
                        add("-cp")
                        add(classPath.joinToString(File.pathSeparator))
                    }
                }
                TargetPlatformDetector.getPlatform(module).subplatformsOfType<JdkPlatform>().firstOrNull()?.targetVersion?.let {
                    with(javaParameters.programParametersList) {
                        add("-jvm-target")
                        add(it.description)
                    }
                }
            }

            with(javaParameters.programParametersList) {
                add("-kotlin-home")
                add(KotlinArtifacts.instance.kotlincDirectory.also {
                    check(it.exists()) {
                        "Kotlinc directory does not exist"
                    }
                }.absolutePath)
            }

            return javaParameters.toCommandLine()
        }
    }
}
