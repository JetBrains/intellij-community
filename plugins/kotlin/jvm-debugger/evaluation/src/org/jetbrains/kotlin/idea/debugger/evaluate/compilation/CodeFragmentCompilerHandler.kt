// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.evaluate.evaluationException
import org.jetbrains.kotlin.idea.debugger.evaluate.getResolutionFacadeForCodeFragment
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.resolve.BindingContext

class CodeFragmentCompilerHandler(val strategy: CodeFragmentCompilingStrategy) {

    fun compileCodeFragment(
        codeFragment: KtCodeFragment,
        moduleDescriptor: ModuleDescriptor,
        bindingContext: BindingContext,
        executionContext: ExecutionContext
    ): CodeFragmentCompiler.CompilationResult {
        return doCompileCodeFragment(strategy, codeFragment, moduleDescriptor, bindingContext, executionContext)
    }

    private fun doCompileCodeFragment(
        strategy: CodeFragmentCompilingStrategy,
        codeFragment: KtCodeFragment,
        moduleDescriptor: ModuleDescriptor,
        bindingContext: BindingContext,
        executionContext: ExecutionContext
    ): CodeFragmentCompiler.CompilationResult {
        val (newBindingContext, filesToCompile) = runReadAction {
            val resolutionFacade = getResolutionFacadeForCodeFragment(codeFragment)
            try {
                val filesToCompile = strategy.getFilesToCompile(resolutionFacade, bindingContext)
                val analysis = resolutionFacade.analyzeWithAllCompilerChecks(filesToCompile)
                Pair(analysis.bindingContext, filesToCompile)
            } catch (e: IllegalArgumentException) {
                evaluationException(e.message ?: e.toString())
            }
        }

        return try {
            CodeFragmentCompiler(executionContext).compile(codeFragment, filesToCompile, strategy, newBindingContext, moduleDescriptor)
        } catch (e: CodeFragmentCodegenException) {
            strategy.processError(e)
            val fallback = strategy.getFallbackStrategy()
            if (fallback != null) {
                return doCompileCodeFragment(fallback, codeFragment, moduleDescriptor, bindingContext, executionContext)
            }
            // This error will be recycled into an error message in the Evaluation/Watches result component,
            // and it won't be actually thrown further, so there shouldn't be duplicated error messages
            // in EA dialog / log / wherever else
            throw e
        }
    }
}
