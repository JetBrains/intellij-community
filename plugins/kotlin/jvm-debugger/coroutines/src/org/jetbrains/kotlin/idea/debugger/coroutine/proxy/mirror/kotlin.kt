// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.ObjectReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class CoroutineStackFrame(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfCoroutineStackFrame>("kotlinx.coroutines.debug.internal.StackTraceFrame", context) {
    private val callerFrame by FieldMirrorDelegate("callerFrame", this)
    private val stackTraceElement by FieldMirrorDelegate("stackTraceElement", StackTraceElement(context))

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfCoroutineStackFrame {
        val callerFrame = callerFrame.mirror(value, context)
        val stackTraceElement = stackTraceElement.mirror(value, context)
        return MirrorOfCoroutineStackFrame(value, callerFrame, stackTraceElement)
    }
}
