// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k1.scratch.compile

import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaCompilationResult
import org.jetbrains.kotlin.analysis.api.components.KaCompilerTarget
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnostic
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.base.codeInsight.compiler.compileToDirectory
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.jvm.k1.scratch.compile.KtScratchSourceFileProcessor.Result
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.LOG
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.printDebugMessage
import org.jetbrains.kotlin.idea.util.JavaParametersBuilder
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
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
        val psiFile = file.ktFile ?: return executor.errorOccurs(
            KotlinJvmBundle.message("couldn.t.find.ktfile.for.current.editor"),
            isFatal = true
        )

        val expressions = file.getExpressions()
        if (!executor.checkForErrors(psiFile, expressions)) return

        when (val result = runReadAction { KtScratchSourceFileProcessor().process(expressions) }) {
            is Result.Error -> return executor.errorOccurs(result.message, isFatal = true)
            is Result.OK -> {
                LOG.printDebugMessage("After processing by KtScratchSourceFileProcessor:\n ${result.code}")
                executeInBackground(KotlinJvmBundle.message("running.kotlin.scratch")) { indicator ->
                    backgroundProcessIndicator = indicator
                    val modifiedScratchSourceFile = createFileWithLightClassSupport(result, psiFile)
                    tryRunCommandLine(modifiedScratchSourceFile, psiFile, result, callback)
                }
            }
        }
    }

    private fun executeInBackground(@NlsContexts.ProgressTitle title: String, block: (indicator: ProgressIndicator) -> Unit) {
        object : Task.Backgroundable(file.project, title, true) {
            override fun run(indicator: ProgressIndicator) = block.invoke(indicator)
        }.queue()
    }

    private fun createFileWithLightClassSupport(result: Result.OK, psiFile: KtFile): KtFile =
        runReadAction { KtPsiFactory.contextual(psiFile).createPhysicalFile("tmp.kt", result.code) }

    private fun tryRunCommandLine(modifiedScratchSourceFile: KtFile, psiFile: KtFile, result: Result.OK, callback: () -> Unit) {
        assert(backgroundProcessIndicator != null)
        try {
            runCommandLine(
                file.project, modifiedScratchSourceFile, file.getExpressions(), psiFile, result,
                backgroundProcessIndicator!!, callback
            )
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            reportError(result, e, psiFile)
        }
    }

    private fun reportError(result: Result.OK, e: Throwable, psiFile: KtFile) {
        LOG.printDebugMessage(result.code)
        executor.errorOccurs(e.message ?: KotlinJvmBundle.message("couldn.t.compile.0", psiFile.name), e, isFatal = true)
    }

    private fun runCommandLine(
        project: Project,
        modifiedScratchSourceFile: KtFile,
        expressions: List<ScratchExpression>,
        psiFile: KtFile,
        result: Result.OK,
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

    @OptIn(KaExperimentalApi::class)
    private fun compileFileToTempDir(psiFile: KtFile, expressions: List<ScratchExpression>): File? {
        if (!executor.checkForErrors(psiFile, expressions)) return null

        val tmpDir = FileUtil.createTempDirectory("compile", "scratch")
        LOG.printDebugMessage("Temp output dir: ${tmpDir.path}")

        val result = analyze(psiFile) {
            val configuration = CompilerConfiguration().apply {
                val containingModule = psiFile.module
                if (containingModule != null) {
                    put(CommonConfigurationKeys.MODULE_NAME, containingModule.name)
                }

                put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, psiFile.languageVersionSettings)
            }

            try {
                val compilerTarget = KaCompilerTarget.Jvm(isTestMode = false, compiledClassHandler = null, debuggerExtension = null)
                val allowedErrorFilter: (KaDiagnostic) -> Boolean = { false }

                compileToDirectory(psiFile, configuration, compilerTarget, allowedErrorFilter, tmpDir)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Throwable) {
                LOG.error(e)
                return null
            }
        }

        when (result) {
            is KaCompilationResult.Failure -> {
                LOG.warn("Errors found on analyzing the scratch file. Compilation aborted")
                return null
            }
            is KaCompilationResult.Success -> {
                return tmpDir
            }
        }
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

        ScriptConfigurationsProvider.getInstance(originalFile.project)
            ?.getScriptConfiguration(originalFile)?.let {
                javaParameters.classPath.addAll(it.dependenciesClassPath.map { f -> f.absolutePath })
            }

        val wslConfiguration = JavaCommandLineState.checkCreateWslConfiguration(javaParameters.jdk)
        val request = wslConfiguration?.createEnvironmentRequest(originalFile.project) ?: LocalTargetEnvironmentRequest()

        return request to javaParameters.toCommandLine(request).build()
    }
}
