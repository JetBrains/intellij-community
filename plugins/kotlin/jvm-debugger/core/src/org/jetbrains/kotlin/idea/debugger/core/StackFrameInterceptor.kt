// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.openapi.components.serviceOrNull
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.core.stepping.ContinuationFilter

interface StackFrameInterceptor {
    fun createStackFrame(
        frame: StackFrameProxyImpl,
        debugProcess: DebugProcessImpl
    ): XStackFrame?

    companion object {
        @JvmStatic
        val instance: StackFrameInterceptor?
            get() = serviceOrNull()
    }

    // TODO: find better place for these methods
    fun extractContinuationFilter(suspendContext: SuspendContextImpl): ContinuationFilter? = null
    fun callerLocation(suspendContext: SuspendContextImpl): Location? = null
}
