// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.TestOnly
import org.jetbrains.eval4j.Value
import org.jetbrains.kotlin.codegen.inline.SMAP
import org.jetbrains.kotlin.idea.debugger.BinaryCacheKey
import org.jetbrains.kotlin.idea.debugger.createWeakBytecodeDebugInfoStorage
import org.jetbrains.kotlin.idea.debugger.evaluate.compilation.CompiledDataDescriptor
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.kotlin.types.KotlinType
import java.util.*

class KotlinDebuggerCaches(project: Project) {
    private val cachedCompiledData = CachedValuesManager.getManager(project).createCachedValue(
        {
            CachedValueProvider.Result<MultiMap<String, CompiledDataDescriptor>>(
                MultiMap.create(), PsiModificationTracker.MODIFICATION_COUNT
            )
        }, false
    )

    private val debugInfoCache = CachedValuesManager.getManager(project).createCachedValue(
        {
            CachedValueProvider.Result(
                createWeakBytecodeDebugInfoStorage(),
                PsiModificationTracker.MODIFICATION_COUNT
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

        fun getSmapCached(project: Project, jvmName: JvmClassName, file: VirtualFile): SMAP? {
            return getInstance(project).debugInfoCache.value[BinaryCacheKey(project, jvmName, file)]
        }
    }

    data class Parameter(val callText: String, val type: KotlinType, val value: Value? = null, val error: EvaluateException? = null)

    class ComputedClassNames(val classNames: List<String>, val shouldBeCached: Boolean) {
        @Suppress("FunctionName")
        companion object {
            val EMPTY = Cached(emptyList())

            fun Cached(classNames: List<String>) = ComputedClassNames(classNames, true)
            fun Cached(className: String) = ComputedClassNames(Collections.singletonList(className), true)

            fun NonCached(classNames: List<String>) = ComputedClassNames(classNames, false)
        }

        fun isEmpty() = classNames.isEmpty()

        fun distinct() = ComputedClassNames(classNames.distinct(), shouldBeCached)

        operator fun plus(other: ComputedClassNames) = ComputedClassNames(
            classNames + other.classNames, shouldBeCached && other.shouldBeCached
        )
    }
}