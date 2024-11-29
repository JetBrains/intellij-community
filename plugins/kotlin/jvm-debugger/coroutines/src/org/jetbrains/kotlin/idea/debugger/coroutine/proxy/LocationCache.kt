// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.sun.jdi.Location
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class LocationCache(val context: DefaultExecutionContext) {
    fun createLocation(stackTraceElement: StackTraceElement): Location =
        DebuggerUtilsEx.findOrCreateLocation(context.suspendContext.virtualMachineProxy.virtualMachine, stackTraceElement)
}