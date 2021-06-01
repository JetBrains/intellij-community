// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.compile

import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.core.KotlinCompilerIde
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.scratch.LOG
import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.printDebugMessage
import org.jetbrains.kotlin.idea.util.JavaParametersBuilder
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

class KtScratchExecutionSession(
    private val file: ScratchFile,
    private val executor: KtCompilingExecutor
) {
    companion object {
        private const val TIMEOUT_MS = 30000
    }

    @Volatile
    private var backgroundProcessIndicator: ProgressIndicator? = null

    fun execute(callback: () -> Unit) {
        val psiFile = file.getPsiFile() as? KtFile ?: return executor.errorOccurs(
            KotlinJvmBundle.message("couldn.t.find.ktfile.for.current.editor"),
            isFatal = true
        )

        val expressions = file.getExpressions()
        if (!executor.checkForErrors(psiFile, expressions)) return

        val project = file.project
        when (val result = runReadAction { KtScratchSourceFileProcessor().process(expressions) }) {
            is KtScratchSourceFileProcessor.Result.Error -> return executor.errorOccurs(result.message, isFatal = true)
            is KtScratchSourceFileProcessor.Result.OK -> {
                LOG.printDebugMessage("After processing by KtScratchSourceFileProcessor:\n ${result.code}")

                object : Task.Backgroundable(psiFile.project, KotlinJvmBundle.message("running.kotlin.scratch"), true) {
                    override fun run(indicator: ProgressIndicator) {
                        backgroundProcessIndicator = indicator

                        ReadAction.nonBlocking {
                            val modifiedScratchSourceFile = runReadAction {
                                KtPsiFactory(psiFile.project).createFileWithLightClassSupport("tmp.kt", result.code, psiFile)
                            }

                            try {
                                runCommandLine(project, modifiedScratchSourceFile, expressions, psiFile, result, indicator, callback)
                            } catch (e: Throwable) {
                                if (e is ControlFlowException) throw e

                                LOG.printDebugMessage(result.code)
                                executor.errorOccurs(
                                    e.message ?: KotlinJvmBundle.message("couldn.t.compile.0", psiFile.name),
                                    e,
                                    isFatal = true
                                )
                            }
                        }
                            .inSmartMode(project)
                            .wrapProgress(indicator)
                            .withDocumentsCommitted(project)
                            .executeSynchronously()
                    }
                }.queue()
            }
        }


    }

    private fun runCommandLine(
        project: Project,
        modifiedScratchSourceFile: KtFile,
        expressions: List<ScratchExpression>,
        psiFile: KtFile,
        result: KtScratchSourceFileProcessor.Result.OK,
        indicator: ProgressIndicator,
        callback: () -> Unit
    ) {
        val tempDir = DumbService.getInstance(project).runReadActionInSmartMode(Computable {
            compileFileToTempDir(modifiedScratchSourceFile, expressions)
        }) ?: return

        try {
            val (environmentRequest, commandLine) = createCommandLine(psiFile, file.module, result.mainClassName, tempDir.path)
            val environment = environmentRequest.prepareEnvironment(TargetProgressIndicatorAdapter(indicator))

            val commandLinePresentation = commandLine.getCommandPresentation(environment)
            LOG.printDebugMessage(commandLinePresentation)

            val processHandler = CapturingProcessHandler(environment.createProcess(commandLine, indicator), null, commandLinePresentation)
            val executionResult = processHandler.runProcessWithProgressIndicator(indicator, TIMEOUT_MS)
            when {
                executionResult.isTimeout -> {
                    executor.errorOccurs(
                        KotlinJvmBundle.message(
                            "couldn.t.get.scratch.execution.result.stopped.by.timeout.0.ms",
                            TIMEOUT_MS
                        )
                    )
                }
                executionResult.isCancelled -> {
                    // ignore
                }
                else -> {
                    executor.parseOutput(executionResult, expressions)
                }
            }
        } finally {
            tempDir.delete()
            callback()
        }
    }

    fun stop() {
        backgroundProcessIndicator?.cancel()
    }

    private fun compileFileToTempDir(psiFile: KtFile, expressions: List<ScratchExpression>): File? {
        if (!executor.checkForErrors(psiFile, expressions)) return null

        val tmpDir = FileUtil.createTempDirectory("compile", "scratch")
        LOG.printDebugMessage("Temp output dir: ${tmpDir.path}")

        KotlinCompilerIde(psiFile).compileToDirectory(tmpDir)
        return tmpDir
    }

    private fun createCommandLine(originalFile: KtFile, module: Module?, mainClassName: String, tempOutDir: String): Pair<TargetEnvironmentRequest, TargetedCommandLine> {
        val javaParameters = JavaParametersBuilder(originalFile.project)
            .withSdkFrom(module, true)
            .withMainClassName(mainClassName)
            .build()

        javaParameters.classPath.add(tempOutDir)

        if (module != null) {
            javaParameters.classPath.addAll(JavaParametersBuilder.getModuleDependencies(module))
        }

        ScriptConfigurationManager.getInstance(originalFile.project)
            .getConfiguration(originalFile)?.let {
                javaParameters.classPath.addAll(it.dependenciesClassPath.map { f -> f.absolutePath })
            }

        val wslConfiguration = JavaCommandLineState.checkCreateWslConfiguration(javaParameters.jdk)
        val request = wslConfiguration?.createEnvironmentRequest(originalFile.project) ?: LocalTargetEnvironmentRequest()

        return request to javaParameters.toCommandLine(request).build()
    }
}