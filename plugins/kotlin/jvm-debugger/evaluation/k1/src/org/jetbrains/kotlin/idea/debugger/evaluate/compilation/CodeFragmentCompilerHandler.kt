// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.extractExceptionCauseClass
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext

class CodeFragmentCompilerHandler(val strategy: CodeFragmentCompilingStrategy) {

    fun compileCodeFragment(
        codeFragment: KtCodeFragment,
        moduleDescriptor: ModuleDescriptor,
        bindingContext: BindingContext,
        executionContext: ExecutionContext
    ): CompilationResult {
        return doCompileCodeFragment(strategy, codeFragment, moduleDescriptor, bindingContext, executionContext)
    }

    private fun doCompileCodeFragment(
        strategy: CodeFragmentCompilingStrategy,
        codeFragment: KtCodeFragment,
        moduleDescriptor: ModuleDescriptor,
        bindingContext: BindingContext,
        executionContext: ExecutionContext
    ): CompilationResult {
        var filesToCompileExceptCodeFragment: List<KtFile> = emptyList()
        return try {
            val result = strategy.stats.startAndMeasureAnalysisUnderReadAction {
                val resolutionFacade = getResolutionFacadeForCodeFragment(codeFragment)
                val filesToCompile = strategy.getFilesToCompile(resolutionFacade, bindingContext)
                val analysis = resolutionFacade.analyzeWithAllCompilerChecks(filesToCompile)
                Pair(analysis.bindingContext, filesToCompile)
            }
            val (newBindingContext, filesToCompile) = result.getOrThrow()
            filesToCompileExceptCodeFragment = filesToCompile.filter { it !== codeFragment }
            CodeFragmentCompiler(executionContext).compile(codeFragment, filesToCompile, strategy, newBindingContext, moduleDescriptor)
                .also {
                    strategy.onSuccess()
                }
        } catch (e: Exception) {
            strategy.stats.compilerFailExceptionClass = extractExceptionCauseClass(e)

            strategy.processError(e, codeFragment, filesToCompileExceptCodeFragment, executionContext)
            // This error will be recycled into an error message in the Evaluation/Watches result component,
            // and it won't be actually thrown further, so there shouldn't be duplicated error messages
            // in EA dialog / log / wherever else
            throw e
        }
    }
}
