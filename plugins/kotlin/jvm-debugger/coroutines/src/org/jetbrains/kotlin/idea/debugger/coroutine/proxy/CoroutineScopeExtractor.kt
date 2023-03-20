// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.coroutine.util.isCoroutineScope

object CoroutineScopeExtractor {
    private var debugProcess: DebugProcess? = null
    private var getContextMethod: Method? = null
    private var getMethod: Method? = null
    private var jobKey: Value? = null

    /*
     * This method extracts coroutine scope from a continuation by evaluating:
     * kotlin.coroutines.coroutineContext[Job]!! as CoroutineScope
     */
    fun extractCoroutineScope(continuation: ObjectReference, evaluationContext: EvaluationContext): ObjectReference? {
        try {
            if (debugProcess != evaluationContext.debugProcess) {
                init(evaluationContext)
            }

            val coroutineContext = continuation.getCoroutineContext(evaluationContext) ?: return null
            val coroutineScope = coroutineContext.getCoroutineScopeByJobKey(evaluationContext) ?: return null
            if (coroutineScope.referenceType().isCoroutineScope()) {
                return coroutineScope
            }
        } catch (ex: Exception) {
        }
        return null
    }

    private fun init(evaluationContext: EvaluationContext) {
        debugProcess = evaluationContext.debugProcess
        val jobType = evaluationContext.findClass("kotlinx.coroutines.Job")
        val jobKeyField = jobType?.fieldByName("Key")
        if (jobType != null && jobKeyField != null) {
            jobKey = jobType.getValue(jobKeyField)
        } else {
            jobKey = null
        }

        val continuationType = evaluationContext.findClass("kotlin.coroutines.Continuation")
        val coroutineContextType = evaluationContext.findClass("kotlin.coroutines.CoroutineContext")
        getContextMethod = continuationType.findMethod("getContext", "()Lkotlin/coroutines/CoroutineContext;")
        getMethod = coroutineContextType.findMethod(
            "get",
            "(Lkotlin/coroutines/CoroutineContext\$Key;)Lkotlin/coroutines/CoroutineContext\$Element;"
        )
    }

    private fun ObjectReference.getCoroutineScopeByJobKey(evaluationContext: EvaluationContext): ObjectReference? {
        val jobKey = CoroutineScopeExtractor.jobKey ?: return null
        val getMethod = CoroutineScopeExtractor.getMethod ?: return null
        val coroutineScope = evaluationContext.debugProcess.invokeMethod(
            evaluationContext,
            this,
            getMethod,
            listOf(jobKey)
        )
        return coroutineScope as? ObjectReference
    }

    private fun ObjectReference.getCoroutineContext(evaluationContext: EvaluationContext): ObjectReference? {
        val getContextMethod = CoroutineScopeExtractor.getContextMethod ?: return null
        val coroutineContext = evaluationContext.debugProcess.invokeMethod(
            evaluationContext,
            this,
            getContextMethod,
            emptyList()
        )
        return coroutineContext as? ObjectReference
    }

    private fun EvaluationContext.findClass(name: String) =
        debugProcess.findClass(this, name, null)

    private fun ReferenceType?.findMethod(name: String, signature: String) =
        if (this == null) {
            null
        } else {
            DebuggerUtils.findMethod(this, name, signature)
        }
}
