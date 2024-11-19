// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.impl.DebuggerUtilsImpl.logError
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.rt.debugger.coroutines.CoroutinesDebugHelper
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.idea.debugger.base.util.dropInlineSuffix
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.callMethodFromHelper
import org.jetbrains.kotlin.idea.debugger.coroutine.data.ContinuationVariableValueDescriptorImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineStacksInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CreationCoroutineStackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.CoroutineStackTraceData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.FieldVariable
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfBaseContinuationImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.MirrorOfStackFrame

private val LOG by lazy { fileLogger() }

internal class ContinuationHolder private constructor(val context: DefaultExecutionContext) {
    internal val locationCache = LocationCache(context)

    internal fun extractCoroutineStacksInfoData(continuation: ObjectReference): CoroutineStacksInfoData? {
        return try {
            fetchContinuationStack(continuation, context, locationCache)
        } catch (e: Exception) {
            logError("Error while looking for stack frame", e)
            null
        }
    }

    companion object {
        fun instance(context: DefaultExecutionContext) =
            ContinuationHolder(context)
    }
}

internal fun MirrorOfBaseContinuationImpl.spilledValues(context: DefaultExecutionContext): List<JavaValue> {
    return fieldVariables.map {
        it.toJavaValue(that, context)
    }
}

private fun FieldVariable.toJavaValue(continuation: ObjectReference, context: DefaultExecutionContext): JavaValue {
    val valueDescriptor = ContinuationVariableValueDescriptorImpl(
        context,
        continuation,
        fieldName,
        dropInlineSuffix(variableName)
    )
    return JavaValue.create(
        null,
        valueDescriptor,
        context.evaluationContext,
        context.debugProcess.xdebugProcess!!.nodeManager,
        false
    )
}

private fun fetchContinuationStack(
    continuation: ObjectReference?,
    context: DefaultExecutionContext,
    locationCache: LocationCache
): CoroutineStacksInfoData? {
    if (continuation == null) return null
    val array = callMethodFromHelper(CoroutinesDebugHelper::class.java, context, "getCoroutineStackTraceDump", listOf(continuation))

    val values = (array as? ArrayReference)?.values ?: return null
    val json = (values[0] as StringReference).value()
    val continuations = (values[1] as ArrayReference).values.mapNotNull { it as? ObjectReference }
    val coroutineStackTraceData = Json.decodeFromString<CoroutineStackTraceData>(json)
    LOG.assertTrue(
        continuations.size == coroutineStackTraceData.continuationFrames.size,
        "Size of continuations and coroutineStackTraceData must be equal."
    )
    val coroutineStack = mutableListOf<MirrorOfStackFrame>()
    for ((i, continuationMirror) in continuations.withIndex()) {
        val data = coroutineStackTraceData.continuationFrames[i]
        coroutineStack += MirrorOfStackFrame(
            MirrorOfBaseContinuationImpl(
                continuationMirror,
                data.stackTraceElement?.stackTraceElement(),
                data.spilledVariables.map { FieldVariable(it.fieldName, it.variableName) },
            )
        )
    }
    val continuationStackFrames = coroutineStack.mapNotNull { it.toCoroutineStackFrameItem(context, locationCache) }
    val creationStackFrames = coroutineStackTraceData.creationStack?.mapIndexed { index, ste ->
        CreationCoroutineStackFrameItem(locationCache.createLocation(ste.stackTraceElement()), index == 0)
    } ?: emptyList()
    return CoroutineStacksInfoData(continuationStackFrames, creationStackFrames)
}
