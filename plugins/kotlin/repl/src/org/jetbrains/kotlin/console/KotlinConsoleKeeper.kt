// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console

import com.intellij.execution.configurations.CompositeParameterTargetedValue
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.KotlinIdeaReplBundle
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.util.JavaParametersBuilder
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.platform.subplatformsOfType
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists

class KotlinConsoleKeeper(val project: Project) {
    private val consoleMap: MutableMap<VirtualFile, KotlinConsoleRunner> = ConcurrentHashMap()

    fun getConsoleByVirtualFile(virtualFile: VirtualFile) = consoleMap[virtualFile]
    fun putVirtualFileToConsole(virtualFile: VirtualFile, console: KotlinConsoleRunner) = consoleMap.put(virtualFile, console)
    fun removeConsole(virtualFile: VirtualFile) = consoleMap.remove(virtualFile)

    fun run(module: Module, previousCompilationFailed: Boolean = false): KotlinConsoleRunner {
        val path = module.moduleFilePath
        val (environmentRequest, cmdLine) = createReplCommandLine(project, module)
        val consoleRunner = KotlinConsoleRunner(
            module,
            environmentRequest,
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
        fun getInstance(project: Project): KotlinConsoleKeeper = project.service()

        fun createReplCommandLine(project: Project, module: Module?): Pair<TargetEnvironmentRequest, TargetedCommandLine> {
            val javaParameters = JavaParametersBuilder(project)
                .withSdkFrom(module, true)
                .withMainClassName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                .build()

            val wslConfiguration = JavaCommandLineState.checkCreateWslConfiguration(javaParameters.jdk)
            val request = wslConfiguration?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest()

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
                val classPath = listOf( // KotlinPaths.ClassPaths.CompilerWithScripting + jetbrains-annotations
                    KotlinArtifacts.kotlinCompiler,
                    KotlinArtifacts.kotlinStdlib,
                    KotlinArtifacts.kotlinReflect,
                    KotlinArtifacts.kotlinScriptRuntime,
                    KotlinArtifacts.kotlinDaemon,
                    KotlinArtifacts.kotlinScriptingCompiler,
                    KotlinArtifacts.kotlinScriptingCompilerImpl,
                    KotlinArtifacts.kotlinScriptingCommon,
                    KotlinArtifacts.kotlinScriptingJvm,
                    KotlinArtifacts.jetbrainsAnnotations
                )
                addAll(classPath.map { file ->
                    val path = file.toPath()
                    val absolutePath = path.absolutePathString()
                    if (path.notExists()) {
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
                        val compositeValue = CompositeParameterTargetedValue()
                        for ((index, s) in classPath.withIndex()) {
                            if (index > 0) {
                                compositeValue.addLocalPart(request.targetPlatform.platform.pathSeparator.toString())
                            }
                            compositeValue.addPathPart(s)
                        }
                        add(compositeValue)
                    }
                }

                module.platform.subplatformsOfType<JdkPlatform>().firstOrNull()?.targetVersion?.let {
                    with(javaParameters.programParametersList) {
                        add("-jvm-target")
                        add(it.description)
                    }
                }
            }

            with(javaParameters.programParametersList) {
                add("-Xrepl")
                add("-kotlin-home")
                val kotlinHome = KotlinPluginLayout.kotlinc
                check(kotlinHome.exists()) { "Kotlin compiler is not found" }
                add(CompositeParameterTargetedValue().addPathPart(kotlinHome.toPath().absolutePathString()))
            }

            return request to javaParameters.toCommandLine(request).build()
        }
    }
}
