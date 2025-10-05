// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineStackFrameProxyImpl
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CodeFragmentCompilationStats
import org.jetbrains.kotlin.psi.*

private class KotlinCodeFragmentPatcher(val codeFragment: KtCodeFragment) {
    private val expressionWrappers = mutableListOf<KotlinCodeFragmentWrapper>()

    fun addWrapper(wrapper: KotlinCodeFragmentWrapper): KotlinCodeFragmentPatcher {
        expressionWrappers.add(wrapper)
        return this
    }

    fun wrapFragmentExpressionIfNeeded() {
        for (wrapper in expressionWrappers)
            wrapper.transformIfNeeded(codeFragment)
    }
}

fun patchCodeFragment(context: ExecutionContext, codeFragment: KtCodeFragment, stats: CodeFragmentCompilationStats) {
    KotlinCodeFragmentPatcher(codeFragment)
        .addWrapper(KotlinValueClassToStringWrapper(stats))
        .addWrapper(
            KotlinSuspendFunctionWrapper(
                stats,
                context,
                codeFragment.context,
                (context.frameProxy as? CoroutineStackFrameProxyImpl)?.isCoroutineScopeAvailable() ?: false
            )
        ).wrapFragmentExpressionIfNeeded()
}
