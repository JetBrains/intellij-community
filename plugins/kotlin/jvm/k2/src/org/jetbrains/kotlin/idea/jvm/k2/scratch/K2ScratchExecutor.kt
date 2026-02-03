// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.execution.JavaParametersBuilder
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.PathUtil
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.script.k2.definitions.KOTLIN_SCRATCH_EXPLAIN_FILE
import org.jetbrains.kotlin.idea.core.script.k2.definitions.KotlinScratchScript
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExecutor
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ExplainInfo
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutput
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputType
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readLines

class K2ScratchExecutor(override val scratchFile: K2KotlinScratchFile, val project: Project, val scope: CoroutineScope) :
    ScratchExecutor(scratchFile) {
    override fun execute() {
        handler.onStart(scratchFile)

        scope.launch {
            try {
                processExecution(scratchFile.virtualFile)
            } catch (e: Exception) {
                handler.error(scratchFile, e.message ?: "Unknown error")
            } finally {
                handler.onFinish(scratchFile)
            }
        }
    }

    private suspend fun processExecution(scriptFile: VirtualFile) {
        val document = readAction { scriptFile.findDocument() }
        if (document == null) {
            handler.error(scratchFile, "Cannot find document: ${scriptFile.path}")
            return
        }

        edtWriteAction {
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        }

        val (code, stdout, stderr) = withBackgroundProgress(
            project, title = KotlinJvmBundle.message("progress.title.compiling.kotlin.scratch")
        ) {
            val process = getJavaCommandLine(scratchFile.virtualFile, scratchFile.currentModule).createProcess()
            process.awaitExit()
            val stdout = withContext(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderr = withContext(Dispatchers.IO) {
                process.errorStream.bufferedReader().use { it.readText() }
            }

            CompilationResult(process.exitValue(), stdout, stderr)
        }

        if (code == 0) {
            if (stdout.isNotEmpty()) {
                handler.handle(scratchFile, ScratchOutput(stdout, ScratchOutputType.OUTPUT))
            }

            val explanations = scriptFile.explainFilePath.readLines().associate {
                it.substringBefore('=', "") to it.substringAfter('=')
            }.filterKeys { it.isNotBlank() }.map { (key, value) ->
                val leftBracketIndex = key.indexOf("(")
                val rightBracketIndex = key.indexOf(")")
                val commaIndex = key.indexOf(",")

                val offsets =
                    key.substring(leftBracketIndex + 1, commaIndex).toInt() to key.substring(commaIndex + 2, rightBracketIndex)
                        .toInt()

                ExplainInfo(
                    key.substring(0, leftBracketIndex), offsets, value, scratchFile.getPsiFile()?.getLineNumber(offsets.second)
                )
            }

            handler.handle(scratchFile, explanations, scope)
        } else if (!scratchFile.options.isInteractiveMode) {
            handler.error(scratchFile, "Compilation failed: $stderr")
        }
    }

    private fun getJavaCommandLine(scriptVirtualFile: VirtualFile, module: Module?): GeneralCommandLine {
        val javaParameters = JavaParametersBuilder(project)
            .withSdkFrom(module)
            .withMainClassName("org.jetbrains.kotlin.preloading.Preloader").build()

        javaParameters.charset = null
        javaParameters.vmParametersList.add("-D$KOTLIN_SCRATCH_EXPLAIN_FILE=${scriptVirtualFile.explainFilePath}")

        val classPath = buildSet {
            add(ideaScriptingJar)
            addAll(requiredKotlinArtifacts)
            if (module != null) addAll(JavaParametersBuilder.getModuleDependencies(module))
        }

        javaParameters.classPath.add(KotlinArtifacts.kotlinPreloader.absolutePath)
        javaParameters.programParametersList.addAll(
            "-cp",
            KotlinArtifacts.kotlinCompiler.absolutePath,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-cp",
            classPath.joinToString(File.pathSeparator),
            "-kotlin-home",
            KotlinPluginLayout.kotlinc.absolutePath,
            "-script",
            scriptVirtualFile.path,
            "-Xplugin=${KotlinArtifacts.powerAssertPlugin.absolutePath}",
            "-P",
            "plugin:kotlin.scripting:script-templates=${KotlinScratchScript::class.java.name}",
            "-Xuse-fir-lt=false",
            "-Xallow-any-scripts-in-source-roots",
            "-P",
            "plugin:kotlin.scripting:disable-script-definitions-autoloading=true",
            "-P",
            "plugin:kotlin.scripting:disable-standard-script=true",
            "-P",
            "plugin:kotlin.scripting:enable-script-explanation=true",
        )

        return javaParameters.toCommandLine()
    }

    private val requiredKotlinArtifacts by lazy {
        listOf(
            KotlinArtifacts.kotlinCompiler,
            KotlinArtifacts.kotlinStdlib,
            KotlinArtifacts.kotlinReflect,
            KotlinArtifacts.kotlinScriptRuntime,
            KotlinArtifacts.trove4j,
            KotlinArtifacts.kotlinDaemon,
            KotlinArtifacts.powerAssertPlugin,
            KotlinArtifacts.kotlinScriptingCompiler,
            KotlinArtifacts.kotlinScriptingCompilerImpl,
            KotlinArtifacts.kotlinScriptingCommon,
            KotlinArtifacts.kotlinScriptingJvm,
            KotlinArtifacts.jetbrainsAnnotations
        ).map { it.toPath() }.filter {
            Files.exists(it)
        }.map { it.absolutePathString() }
    }

    private val ideaScriptingJar by lazy { PathUtil.getJarPathForClass(KotlinScratchScript::class.java) }

    private val explainScratchesDirectory: Path by lazy {
        FileUtilRt.createTempDirectory("kotlin-scratches-explain", null, true).toPath()
    }

    private val VirtualFile.explainFilePath: Path
        get() = explainScratchesDirectory.resolve(this.name.replace(".kts", ".txt"))

    override fun stop() {
        handler.onFinish(scratchFile)
    }

    private data class CompilationResult(
        val code: Int,
        val stdout: String,
        val stderr: String,
    )
}