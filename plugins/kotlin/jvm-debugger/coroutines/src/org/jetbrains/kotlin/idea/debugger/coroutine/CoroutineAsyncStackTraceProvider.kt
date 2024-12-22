// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.engine.CreationStackTraceProvider
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutinePreflightFrame

private class CoroutineAsyncStackTraceProvider : CreationStackTraceProvider {
    override fun getCreationStackTrace(stackFrame: JavaStackFrame, suspendContext: SuspendContextImpl): List<StackFrameItem?>? {
        return (stackFrame as? CoroutinePreflightFrame)?.coroutineStacksInfoData?.creationStackFrames?.takeIf { it.isNotEmpty() }
    }
}