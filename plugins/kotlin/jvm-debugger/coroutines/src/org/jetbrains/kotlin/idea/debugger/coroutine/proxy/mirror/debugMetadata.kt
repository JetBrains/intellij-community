// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class DebugMetadata private constructor(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfDebugProbesImpl>("kotlin.coroutines.jvm.internal.DebugMetadataKt", context) {
    private val getStackTraceElementMethod by MethodMirrorDelegate("getStackTraceElement", StackTraceElement(context))
    private val getSpilledVariableFieldMappingMethod by MethodDelegate<ArrayReference>("getSpilledVariableFieldMapping",
                                                                                       "(Lkotlin/coroutines/jvm/internal/BaseContinuationImpl;)[Ljava/lang/String;")
    private val baseContinuationImpl = BaseContinuationImpl(context, this)

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfDebugProbesImpl =
            throw IllegalStateException("Not meant to be mirrored.")

    internal fun fetchContinuationStack(continuation: ObjectReference?, context: DefaultExecutionContext): List<MirrorOfStackFrame> {
        if (continuation == null) return emptyList()
        val coroutineStack = mutableListOf<MirrorOfStackFrame>()
        var loopContinuation: ObjectReference? = continuation
        while (loopContinuation != null) {
            val continuationMirror = try {
                baseContinuationImpl.mirror(loopContinuation, context)
            } catch (e: Throwable) {
                log.error("Could not fetch stack frame for $loopContinuation", e)
                continue
            } ?: break
            coroutineStack.add(MirrorOfStackFrame(continuationMirror))
            loopContinuation = continuationMirror.nextContinuation
        }
        return coroutineStack
    }

    fun getStackTraceElement(value: ObjectReference, context: DefaultExecutionContext) =
            getStackTraceElementMethod.mirror(value, context)

    internal fun getSpilledVariableFieldMapping(value: ObjectReference, context: DefaultExecutionContext) =
            getSpilledVariableFieldMappingMethod.value(value, context)

    companion object {
        val log by logger

        fun instance(context: DefaultExecutionContext): DebugMetadata? {
            try {
                return DebugMetadata(context)
            }
            catch (e: IllegalStateException) {
                log.debug("Attempt to access DebugMetadata but none found.", e)
            }
            return null
        }
    }
}

internal class BaseContinuationImpl(context: DefaultExecutionContext, private val debugMetadata: DebugMetadata) :
        BaseMirror<ObjectReference, MirrorOfBaseContinuationImpl>("kotlin.coroutines.jvm.internal.BaseContinuationImpl", context) {

    private val getCompletion by MethodMirrorDelegate("getCompletion", this, "()Lkotlin/coroutines/Continuation;")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfBaseContinuationImpl {
        val stackTraceElementMirror = debugMetadata.getStackTraceElement(value, context)
        val fieldVariables = getSpilledVariableFieldMapping(value, context)
        val completionValue = getCompletion.value(value, context)
        return MirrorOfBaseContinuationImpl(
            value,
            stackTraceElementMirror?.stackTraceElement(),
            fieldVariables,
            getNextContinuation(completionValue),
            getCoroutineOwner(completionValue)
        )
    }

    private fun getCoroutineOwner(completion: ObjectReference?) =
        if (completion != null && DebugProbesImplCoroutineOwner.instanceOf(completion)) completion else null

    private fun getNextContinuation(completion: ObjectReference?) =
        if (completion != null && getCompletion.isCompatible(completion)) completion else null

    fun getSpilledVariableFieldMapping(value: ObjectReference, context: DefaultExecutionContext): List<FieldVariable> {
        val getSpilledVariableFieldMappingReference =
                debugMetadata.getSpilledVariableFieldMapping(value, context) ?: return emptyList()

        val fieldVariables = mutableListOf<FieldVariable>()
        val values = getSpilledVariableFieldMappingReference.values // fetch all values at once
        for (index in 0 until values.size / 2) {
            val fieldName = values[2 * index].safeAs<StringReference>()?.value() ?: continue
            val variableName = values[2 * index + 1].safeAs<StringReference>()?.value() ?: continue
            fieldVariables.add(FieldVariable(fieldName, variableName))
        }
        return fieldVariables
    }
}

class CoroutineStackFrameLight(context: DefaultExecutionContext): BaseMirror<ObjectReference, MirrorOfBaseContinuationImplLight>("kotlin.coroutines.jvm.internal.CoroutineStackFrame", context) {
    private val getCallerFrame by MethodMirrorDelegate("getCallerFrame", this)

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfBaseContinuationImplLight {
        val completionValue = getCallerFrame.value(value, context)
        return MirrorOfBaseContinuationImplLight(
            value,
            completionValue?.takeIf { getCallerFrame.isCompatible(it) },
            completionValue?.takeIf { DebugProbesImplCoroutineOwner.instanceOf(it) }
        )
    }
}