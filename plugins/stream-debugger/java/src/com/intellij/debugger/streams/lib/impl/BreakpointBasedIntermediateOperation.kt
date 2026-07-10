// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.IntermediateOperation
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.trace.breakpoint.instrumentation.IntermediateCallHandler
import com.sun.jdi.ObjectReference

interface BreakpointBasedIntermediateOperation : IntermediateOperation {
  fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler
}
