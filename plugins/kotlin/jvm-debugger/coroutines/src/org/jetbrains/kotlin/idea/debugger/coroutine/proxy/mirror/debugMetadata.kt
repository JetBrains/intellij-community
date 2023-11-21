// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger

class DebugMetadata private constructor(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfDebugProbesImpl>("kotlin.coroutines.jvm.internal.DebugMetadataKt", context) {
    private val getStackTraceElementMethod by MethodMirrorDelegate("getStackTraceElement", StackTraceElement(context))
    private val getSpilledVariableFieldMappingMethod by MethodDelegate<ArrayReference>("getSpilledVariableFieldMapping",
                                                                                       "(Lkotlin/coroutines/jvm/internal/BaseContinuationImpl;)[Ljava/lang/String;")
    val baseContinuationImpl = BaseContinuationImpl(context, this)

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfDebugProbesImpl =
            throw IllegalStateException("Not meant to be mirrored.")

    fun fetchContinuationStack(continuation: ObjectReference, context: DefaultExecutionContext): MirrorOfContinuationStack {
        val coroutineStack = mutableListOf<MirrorOfStackFrame>()
        var loopContinuation: ObjectReference? = continuation
        while (loopContinuation != null) {
            val continuationMirror = baseContinuationImpl.mirror(loopContinuation, context) ?: break
            coroutineStack.add(MirrorOfStackFrame(loopContinuation, continuationMirror))
            loopContinuation = continuationMirror.nextContinuation
        }
        return MirrorOfContinuationStack(continuation, coroutineStack)
    }

    fun getStackTraceElement(value: ObjectReference, context: DefaultExecutionContext) =
            getStackTraceElementMethod.mirror(value, context)

    fun getSpilledVariableFieldMapping(value: ObjectReference, context: DefaultExecutionContext) =
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

class BaseContinuationImpl(context: DefaultExecutionContext, private val debugMetadata: DebugMetadata) :
        BaseMirror<ObjectReference, MirrorOfBaseContinuationImpl>("kotlin.coroutines.jvm.internal.BaseContinuationImpl", context) {

    private val getCompletion by MethodMirrorDelegate("getCompletion", this, "()Lkotlin/coroutines/Continuation;")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfBaseContinuationImpl {
        val stackTraceElementMirror = debugMetadata.getStackTraceElement(value, context)
        val fieldVariables = getSpilledVariableFieldMapping(value, context)
        val completionValue = getCompletion.value(value, context)
        return MirrorOfBaseContinuationImpl(
            value,
            stackTraceElementMirror,
            fieldVariables,
            getNextContinuation(completionValue),
            getCoroutineOwner(completionValue)
        )
    }

    fun getNextContinuation(value: ObjectReference, context: DefaultExecutionContext): ObjectReference? =
        getNextContinuation(getCompletion.value(value, context))

    private fun getCoroutineOwner(completion: ObjectReference?) =
        if (completion != null && DebugProbesImplCoroutineOwner.instanceOf(completion)) completion else null

    private fun getNextContinuation(completion: ObjectReference?) =
        if (completion != null && getCompletion.isCompatible(completion)) completion else null

    fun getSpilledVariableFieldMapping(value: ObjectReference, context: DefaultExecutionContext): List<FieldVariable> {
        val getSpilledVariableFieldMappingReference =
                debugMetadata.getSpilledVariableFieldMapping(value, context) ?: return emptyList()

        val length = getSpilledVariableFieldMappingReference.length() / 2
        val fieldVariables = ArrayList<FieldVariable>()
        for (index in 0 until length) {
            val fieldVariable = getFieldVariableName(getSpilledVariableFieldMappingReference, index) ?: continue
            fieldVariables.add(fieldVariable)
        }
        return fieldVariables
    }

    private fun getFieldVariableName(rawSpilledVariables: ArrayReference, index: Int): FieldVariable? {
        val fieldName = (rawSpilledVariables.getValue(2 * index) as? StringReference)?.value() ?: return null
        val variableName = (rawSpilledVariables.getValue(2 * index + 1) as? StringReference)?.value() ?: return null
        return FieldVariable(fieldName, variableName)
    }
}

class BaseContinuationImplLight(context: DefaultExecutionContext) : BaseMirror<ObjectReference, MirrorOfBaseContinuationImplLight>("kotlin.coroutines.jvm.internal.BaseContinuationImpl", context) {
    private val getCompletion by FieldMirrorDelegate("completion", this)

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfBaseContinuationImplLight {
        val completionValue = getCompletion.value(value)
        return MirrorOfBaseContinuationImplLight(
            value,
            completionValue?.takeIf { getCompletion.isCompatible(it) },
            completionValue?.takeIf { DebugProbesImplCoroutineOwner.instanceOf(it) }
        )
    }
}
