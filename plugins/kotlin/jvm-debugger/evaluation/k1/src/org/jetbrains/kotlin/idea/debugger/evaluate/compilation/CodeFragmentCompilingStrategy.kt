// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.kotlin.idea.core.util.CodeFragmentUtils
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.CompilerType
import org.jetbrains.kotlin.idea.debugger.evaluate.EvaluationCompilerResult
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerEvaluatorStatisticsCollector
import org.jetbrains.kotlin.idea.debugger.evaluate.gatherProjectFilesDependedOnByFragment
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.util.concurrent.ExecutionException

abstract class CodeFragmentCompilingStrategy(val codeFragment: KtCodeFragment) {
    internal val stats: CodeFragmentCompilationStats = CodeFragmentCompilationStats()

    abstract fun getFilesToCompile(resolutionFacade: ResolutionFacade, bindingContext: BindingContext): List<KtFile>

    abstract fun onSuccess()
    abstract fun processError(e: Throwable, codeFragment: KtCodeFragment, otherFiles: List<KtFile>, executionContext: ExecutionContext)
    abstract fun beforeAnalyzingCodeFragment()

    protected fun unwrapException(e: Throwable?): Throwable? = when (e) {
        is CodeFragmentCodegenException -> e.reason
        is ExecutionException -> unwrapException(e.cause)
        is ProcessCanceledException -> null
        else -> e
    }
}

class IRCodeFragmentCompilingStrategy(codeFragment: KtCodeFragment) : CodeFragmentCompilingStrategy(codeFragment) {
    override fun getFilesToCompile(resolutionFacade: ResolutionFacade, bindingContext: BindingContext): List<KtFile> {
        // The IR Evaluator is sensitive to the analysis order of files in fragment compilation:
        // The codeFragment must be passed _last_ to analysis such that the result is stacked at
        // the _bottom_ of the composite analysis result.
        //
        // The situation as seen from here is as follows:
        //   1) `analyzeWithAllCompilerChecks` analyze each individual file passed to it separately.
        //   2) The individual results are "stacked" on top of each other.
        //   3) With distinct files, "stacking on top" is equivalent to "side by side" - there is
        //      no overlap in what is analyzed, so the order doesn't matter: the composite analysis
        //      result is just a look-up mechanism for convenience.
        //   4) Code Fragments perform partial analysis of the context of the fragment, e.g. a
        //      breakpoint in a function causes partial analysis of the surrounding function.
        //   5) If the surrounding function is _also_ included in the `filesToCompile`, that
        //      function will be analyzed more than once: in particular, fresh symbols will be
        //      allocated anew upon repeated analysis.
        //   6) Now the order of composition is significant: layering the fragment at the bottom
        //      ensures code that needs a consistent view of the entire function (i.e. psi2ir)
        //      does not mix the fresh, partial view of the function in the fragment analysis with
        //      the complete analysis from the separate analysis of the entire file included in the
        //      compilation.
        //
        fun <T> MutableList<T>.moveToLast(element: T) {
            removeAll(listOf(element))
            add(element)
        }

        return gatherProjectFilesDependedOnByFragment(
            codeFragment,
            bindingContext
        ).toMutableList().apply {
            moveToLast(codeFragment)
        }
    }

    override fun onSuccess() {
        KotlinDebuggerEvaluatorStatisticsCollector.logAnalysisAndCompilationResult(
            codeFragment.project,
            CompilerType.IR,
            EvaluationCompilerResult.SUCCESS,
            stats
        )
    }

    override fun processError(e: Throwable, codeFragment: KtCodeFragment, otherFiles: List<KtFile>, executionContext: ExecutionContext) {
        val exceptionToReport = unwrapException(e) ?: throw e
        KotlinDebuggerEvaluatorStatisticsCollector.logAnalysisAndCompilationResult(
            codeFragment.project,
            CompilerType.IR,
            EvaluationCompilerResult.COMPILER_INTERNAL_ERROR,
            stats
        )
        if (ApplicationManager.getApplication().isUnitTestMode) {
            throw exceptionToReport
        }
        if (isApplicationInternalMode()) {
            reportErrorWithAttachments(executionContext, codeFragment, exceptionToReport, prepareFilesToCompile(otherFiles))
        }
    }

    override fun beforeAnalyzingCodeFragment() {
        codeFragment.putCopyableUserData(CodeFragmentUtils.USED_FOR_COMPILATION_IN_IR_EVALUATOR, true)
    }

    private fun prepareFilesToCompile(files: List<KtFile>) = buildList<Pair<String, String>> {
        for (file in files) {
            add(file.name to file.text)
        }
    }
}
