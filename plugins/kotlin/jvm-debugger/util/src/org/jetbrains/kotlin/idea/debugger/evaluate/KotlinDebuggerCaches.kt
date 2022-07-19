// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.TestOnly
import org.jetbrains.eval4j.Value
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CompiledDataDescriptor
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.types.KotlinType

class KotlinDebuggerCaches(project: Project) {
    private val cachedCompiledData = CachedValuesManager.getManager(project).createCachedValue(
        {
            CachedValueProvider.Result<MultiMap<String, CompiledDataDescriptor>>(
                MultiMap.create(), PsiModificationTracker.MODIFICATION_COUNT
            )
        }, false
    )

    companion object {
        private val LOG = Logger.getInstance(KotlinDebuggerCaches::class.java)

        @get:TestOnly
        var LOG_COMPILATIONS: Boolean = false

        fun getInstance(project: Project): KotlinDebuggerCaches = project.service()

        fun compileCodeFragmentCacheAware(
            codeFragment: KtCodeFragment,
            sourcePosition: SourcePosition?,
            compileCode: () -> CompiledDataDescriptor,
            force: Boolean = false
        ): Pair<CompiledDataDescriptor, Boolean> {
            if (sourcePosition == null) {
                return Pair(compileCode(), false)
            }

            val evaluateExpressionCache = getInstance(codeFragment.project)

            val text = "${codeFragment.importsToString()}\n${codeFragment.text}"

            val cachedResults = synchronized<Collection<CompiledDataDescriptor>>(evaluateExpressionCache.cachedCompiledData) {
                evaluateExpressionCache.cachedCompiledData.value[text]
            }

            val existingResult = cachedResults.firstOrNull { it.sourcePosition == sourcePosition }
            if (existingResult != null) {
                if (force) {
                    synchronized(evaluateExpressionCache.cachedCompiledData) {
                        evaluateExpressionCache.cachedCompiledData.value.remove(text, existingResult)
                    }
                } else {
                    return Pair(existingResult, true)
                }
            }

            val newCompiledData = compileCode()

            if (LOG_COMPILATIONS) {
                LOG.debug("Compile bytecode for ${codeFragment.text}")
            }

            synchronized(evaluateExpressionCache.cachedCompiledData) {
                evaluateExpressionCache.cachedCompiledData.value.putValue(text, newCompiledData)
            }

            return Pair(newCompiledData, false)
        }
    }

    data class Parameter(val callText: String, val type: KotlinType, val value: Value? = null, val error: EvaluateException? = null)
}