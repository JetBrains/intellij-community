// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.engine.JavaValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.VMDisconnectedException
import org.jetbrains.kotlin.idea.debugger.base.util.dropInlineSuffix
import org.jetbrains.kotlin.idea.debugger.coroutine.data.*
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror.*
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class ContinuationHolder private constructor(val context: DefaultExecutionContext) {
    private val debugMetadata: DebugMetadata? = DebugMetadata.instance(context)
    private val locationCache = LocationCache(context)
    private val debugProbesImpl = DebugProbesImpl.instance(context)

    fun extractCoroutineInfoData(continuation: ObjectReference): CompleteCoroutineInfoData? {
        try {
            val continuationStack = debugMetadata?.fetchContinuationStack(continuation, context) ?: return null
            val continuationStackFrames = continuationStack.mapNotNull { it.toCoroutineStackFrameItem(context, locationCache) }
            val lastRestoredFrame = continuationStack.lastOrNull()
            return findCoroutineInformation(lastRestoredFrame?.baseContinuationImpl?.coroutineOwner, continuationStackFrames)
        } catch (_: VMDisconnectedException) {
        } catch (e: Exception) {
            log.error("Error while looking for stack frame", e)
        }
        return null
    }

    private fun findCoroutineInformation(
        coroutineOwner: ObjectReference?,
        continuationStackFrames: List<CoroutineStackFrameItem>
    ): CompleteCoroutineInfoData {
        val coroutineInfo = debugProbesImpl?.getCoroutineInfo(coroutineOwner, context)
        return if (coroutineInfo != null) {
            val creationStackFrames = coroutineInfo.creationStackTraceProvider.getStackTrace().mapIndexed { index, ste ->
                CreationCoroutineStackFrameItem(locationCache.createLocation(ste.stackTraceElement()), index == 0)
            }
            CompleteCoroutineInfoData(
                descriptor = CoroutineDescriptor.instance(coroutineInfo),
                continuationStackFrames = continuationStackFrames,
                creationStackFrames = creationStackFrames,
                jobHierarchy = emptyList()
            )
        } else {
            CompleteCoroutineInfoData(
                descriptor = CoroutineDescriptor(CoroutineInfoData.DEFAULT_COROUTINE_NAME, "-1", State.UNKNOWN, null, null),
                continuationStackFrames = continuationStackFrames,
                creationStackFrames = emptyList(),
                jobHierarchy = emptyList()
            )
        }
    }

    companion object {
        val log by logger

        fun instance(context: DefaultExecutionContext) =
            ContinuationHolder(context)
    }
}

fun MirrorOfBaseContinuationImpl.spilledValues(context: DefaultExecutionContext): List<JavaValue> {
    return fieldVariables.map {
        it.toJavaValue(that, context)
    }
}

fun FieldVariable.toJavaValue(continuation: ObjectReference, context: DefaultExecutionContext): JavaValue {
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
