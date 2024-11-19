// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.ObjectReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger

class DebugMetadata private constructor(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfDebugProbesImpl>("kotlin.coroutines.jvm.internal.DebugMetadataKt", context) {
    private val getStackTraceElementMethod by MethodMirrorDelegate("getStackTraceElement", StackTraceElement(context))

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfDebugProbesImpl =
            throw IllegalStateException("Not meant to be mirrored.")

    fun getStackTraceElement(value: ObjectReference, context: DefaultExecutionContext) =
            getStackTraceElementMethod.mirror(value, context)

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