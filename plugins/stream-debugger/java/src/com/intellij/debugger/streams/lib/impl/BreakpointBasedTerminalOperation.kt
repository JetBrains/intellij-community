// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.TerminalOperation
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.TerminalCallHandler
import com.sun.jdi.ObjectReference

interface BreakpointBasedTerminalOperation : TerminalOperation {
  fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    call: TerminatorStreamCall,
    time: ObjectReference,
  ): TerminalCallHandler
}
