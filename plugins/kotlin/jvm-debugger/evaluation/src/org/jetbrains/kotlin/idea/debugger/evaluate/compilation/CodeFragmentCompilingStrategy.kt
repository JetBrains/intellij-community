// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate.compilation

import org.jetbrains.kotlin.idea.core.util.analyzeInlinedFunctions
import org.jetbrains.kotlin.idea.debugger.evaluate.LOG
import org.jetbrains.kotlin.idea.debugger.evaluate.gatherProjectFilesDependedOnByFragment
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext

abstract class CodeFragmentCompilingStrategy(val codeFragment: KtCodeFragment) {

    abstract val compilerBackend: FragmentCompilerCodegen

    abstract fun getFilesToCompile(resolutionFacade: ResolutionFacade, bindingContext: BindingContext): List<KtFile>

    abstract fun processError(e: CodeFragmentCodegenException)
    abstract fun getFallbackStrategy(): CodeFragmentCompilingStrategy?
}

class OldCodeFragmentCompilingStrategy(codeFragment: KtCodeFragment) : CodeFragmentCompilingStrategy(codeFragment) {

    override val compilerBackend: FragmentCompilerCodegen = OldFragmentCompilerCodegen(codeFragment)

    override fun getFilesToCompile(resolutionFacade: ResolutionFacade, bindingContext: BindingContext): List<KtFile> {
        return analyzeInlinedFunctions(
            resolutionFacade,
            codeFragment,
            analyzeOnlyReifiedInlineFunctions = false,
        )
    }

    override fun processError(e: CodeFragmentCodegenException) {
        throw e
    }

    override fun getFallbackStrategy(): CodeFragmentCompilingStrategy? = null
}

class IRCodeFragmentCompilingStrategy(codeFragment: KtCodeFragment) : CodeFragmentCompilingStrategy(codeFragment) {


    override val compilerBackend: FragmentCompilerCodegen = IRFragmentCompilerCodegen()

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

    override fun processError(e: CodeFragmentCodegenException) {
        // TODO maybe break down known cases of failures and keep statistics on them?
        //      This way, we will have a complete picture of what exact errors users
        //      come across.
        LOG.error("Error when compiling code fragment with IR evaluator", e.reason)
    }

    override fun getFallbackStrategy(): CodeFragmentCompilingStrategy? {
        // It's better not to fall back when testing the IR evaluator -- otherwise regressions might slip through
        if (isUnitTestMode()) return null

        return OldCodeFragmentCompilingStrategy(codeFragment)
    }
}
