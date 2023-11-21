// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.xdebugger.frame.XStackFrame

interface StackFrameInterceptor {
    fun createStackFrame(
        frame: StackFrameProxyImpl,
        debugProcess: DebugProcessImpl
    ): XStackFrame?
}
