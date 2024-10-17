// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.openapi.util.Key
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.coroutine.util.isCoroutineScope

class CoroutineScopeExtractor(
    private val getContextMethod: Method?,
    private val getMethod: Method?,
    private val jobKey: Value?,
) {

    /**
     * This method extracts coroutine scope from a continuation by evaluating:
     * kotlin.coroutines.coroutineContext[Job]!! as CoroutineScope
     */
    fun extractCoroutineScope(continuation: ObjectReference, evaluationContext: EvaluationContext): ObjectReference? {
        try {
            val coroutineContext = continuation.getCoroutineContext(evaluationContext) ?: return null
            val coroutineScope = coroutineContext.getCoroutineScopeByJobKey(evaluationContext) ?: return null
            if (coroutineScope.referenceType().isCoroutineScope()) {
                return coroutineScope
            }
        } catch (ex: Exception) {
        }
        return null
    }

    companion object {
        val KEY = Key.create<CoroutineScopeExtractor>("CoroutineScopeExtractor")

        fun create(evaluationContext: EvaluationContext): CoroutineScopeExtractor {
            try {
                val jobType = evaluationContext.findClass("kotlinx.coroutines.Job")
                var jobKey: Value? = null
                if (jobType != null) {
                    val jobKeyField = DebuggerUtils.findField(jobType, "Key")
                    if (jobKeyField != null) {
                        jobKey = jobType.getValue(jobKeyField)
                    }
                }

                val continuationType = evaluationContext.findClass("kotlin.coroutines.Continuation")
                val coroutineContextType = evaluationContext.findClass("kotlin.coroutines.CoroutineContext")
                val getContextMethod = continuationType.findMethod("getContext", "()Lkotlin/coroutines/CoroutineContext;")
                val getMethod = coroutineContextType.findMethod(
                    "get",
                    "(Lkotlin/coroutines/CoroutineContext\$Key;)Lkotlin/coroutines/CoroutineContext\$Element;"
                )
                return CoroutineScopeExtractor(getContextMethod, getMethod, jobKey)
            } catch (ex: Exception) {
            }

            return CoroutineScopeExtractor(null, null, null)
        }
    }

    private fun ObjectReference.getCoroutineScopeByJobKey(evaluationContext: EvaluationContext): ObjectReference? {
        val jobKey = jobKey ?: return null
        val getMethod = getMethod ?: return null
        val coroutineScope = evaluationContext.debugProcess.invokeMethod(
            evaluationContext,
            this,
            getMethod,
            listOf(jobKey)
        )
        return coroutineScope as? ObjectReference
    }

    private fun ObjectReference.getCoroutineContext(evaluationContext: EvaluationContext): ObjectReference? {
        val getContextMethod = getContextMethod ?: return null
        val coroutineContext = evaluationContext.debugProcess.invokeMethod(
            evaluationContext,
            this,
            getContextMethod,
            emptyList()
        )
        return coroutineContext as? ObjectReference
    }
}

private fun EvaluationContext.findClass(name: String): ReferenceType? {
    return debugProcess.findClass(this, name, null)
}

private fun ReferenceType?.findMethod(name: String, signature: String): Method? {
    if (this == null) {
        return null
    } else {
        return DebuggerUtils.findMethod(this, name, signature)
    }
}
