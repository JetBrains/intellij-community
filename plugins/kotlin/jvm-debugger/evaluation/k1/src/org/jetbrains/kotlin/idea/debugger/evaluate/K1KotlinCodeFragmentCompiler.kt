// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationOrigin
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluator.Companion.logCompilation
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.*
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.AnalyzingUtils
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import java.util.*

private class K1KotlinCodeFragmentCompiler : KotlinCodeFragmentCompiler {
    override val compilerType: CompilerType = CompilerType.IR

    override fun compileCodeFragment(
      context: ExecutionContext,
      codeFragment: KtCodeFragment
    ): CompiledCodeFragmentData {
        val debugProcess = context.debugProcess

        val compilerStrategy = IRCodeFragmentCompilingStrategy(codeFragment)
        compilerStrategy.stats.origin = XEvaluationOrigin.getOrigin(context.evaluationContext)
        try {
            patchCodeFragment(context, codeFragment, compilerStrategy.stats)
        } catch (e: Exception) {
            compilerStrategy.processError(e, codeFragment, emptyList(), context)
            throw e
        }

        compilerStrategy.beforeAnalyzingCodeFragment()
        val analysisResult = analyze(codeFragment, debugProcess, compilerStrategy)

        analysisResult.illegalSuspendFunCallDiagnostic?.let {
            evaluationException(DefaultErrorMessages.render(it))
        }

        val compilerHandler = CodeFragmentCompilerHandler(compilerStrategy)

        val result =
            compilerHandler.compileCodeFragment(codeFragment, analysisResult.moduleDescriptor, analysisResult.bindingContext, context)

        logCompilation(codeFragment)

        return createCompiledDataDescriptor(result, canBeCached = true)
    }

    private fun analyze(codeFragment: KtCodeFragment, debugProcess: DebugProcessImpl, compilerStrategy: IRCodeFragmentCompilingStrategy): ErrorCheckingResult {
        val result = ReadAction.nonBlocking<Result<ErrorCheckingResult>> {
            try {
                Result.success(doAnalyze(codeFragment, debugProcess, compilerStrategy))
            } catch (ex: ProcessCanceledException) {
                throw ex // Restart the action
            } catch (ex: Exception) {
                Result.failure(ex)
            }
        }.executeSynchronously()
        return result.getOrThrow()
    }

    private fun doAnalyze(codeFragment: KtCodeFragment, debugProcess: DebugProcessImpl, compilerStrategy: IRCodeFragmentCompilingStrategy): ErrorCheckingResult {
        try {
            AnalyzingUtils.checkForSyntacticErrors(codeFragment)
        } catch (e: IllegalArgumentException) {
            evaluationException(e.message ?: e.toString())
        }

        val resolutionFacade = getResolutionFacadeForCodeFragment(codeFragment)

        DebugForeignPropertyDescriptorProvider(codeFragment, debugProcess).supplyDebugForeignProperties()

        val analysisResult = resolutionFacade.analyzeWithAllCompilerChecks(codeFragment)

        if (analysisResult.isError()) {
            evaluationException(analysisResult.error)
        }

        val bindingContext = analysisResult.bindingContext
        reportErrorDiagnosticIfAny(bindingContext, codeFragment, compilerStrategy.stats)
        return ErrorCheckingResult(
            bindingContext,
            analysisResult.moduleDescriptor,
            Collections.singletonList(codeFragment),
            bindingContext.diagnostics.firstOrNull {
                it.isIllegalSuspendFunCallInCodeFragment(codeFragment)
            }
        )
    }

    private data class ErrorCheckingResult(
        val bindingContext: BindingContext,
        val moduleDescriptor: ModuleDescriptor,
        val files: List<KtFile>,
        val illegalSuspendFunCallDiagnostic: Diagnostic?
    )

    private fun reportErrorDiagnosticIfAny(
        bindingContext: BindingContext,
        codeFragment: KtCodeFragment,
        stats: CodeFragmentCompilationStats
    ) {
        bindingContext.diagnostics
            .filter { it.factory !in IGNORED_DIAGNOSTICS }
            .firstOrNull { it.severity == Severity.ERROR && it.psiElement.containingFile == codeFragment }
            ?.let {
                KotlinDebuggerEvaluatorStatisticsCollector.logAnalysisAndCompilationResult(
                    codeFragment.project,
                    CompilerType.IR,
                    EvaluationCompilerResult.COMPILATION_FAILURE,
                    stats
                )
                throw IncorrectCodeFragmentException(DefaultErrorMessages.render(it))
            }
    }

    private fun Diagnostic.isIllegalSuspendFunCallInCodeFragment(codeFragment: KtCodeFragment) =
        severity == Severity.ERROR && psiElement.containingFile == codeFragment &&
                factory == Errors.ILLEGAL_SUSPEND_FUNCTION_CALL

}

private val IGNORED_DIAGNOSTICS: Set<DiagnosticFactory<*>> = Errors.INVISIBLE_REFERENCE_DIAGNOSTICS +
        setOf(
            Errors.OPT_IN_USAGE_ERROR,
            Errors.OPT_IN_TO_INHERITANCE_ERROR,
            Errors.MISSING_DEPENDENCY_SUPERCLASS,
            Errors.IR_WITH_UNSTABLE_ABI_COMPILED_CLASS,
            Errors.FIR_COMPILED_CLASS,
            Errors.ILLEGAL_SUSPEND_FUNCTION_CALL,
            ErrorsJvm.JAVA_MODULE_DOES_NOT_DEPEND_ON_MODULE,
            ErrorsJvm.JAVA_MODULE_DOES_NOT_READ_UNNAMED_MODULE,
            ErrorsJvm.JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE
        )
