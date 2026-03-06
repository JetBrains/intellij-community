// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.streams.core.lib.impl.CollapseOperation
import com.intellij.debugger.streams.core.lib.impl.ConcatOperation
import com.intellij.debugger.streams.core.lib.impl.DistinctOperation
import com.intellij.debugger.streams.core.lib.impl.FilterOperation
import com.intellij.debugger.streams.core.lib.impl.FlatMappingOperation
import com.intellij.debugger.streams.core.lib.impl.MappingOperation
import com.intellij.debugger.streams.core.lib.impl.OrderBasedOperation
import com.intellij.debugger.streams.core.lib.impl.SortedOperation
import com.intellij.debugger.streams.core.lib.impl.ToCollectionOperation
import com.intellij.debugger.streams.core.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.trace.impl.handler.unified.DistinctByKeyHandler
import com.intellij.debugger.streams.core.trace.impl.handler.unified.DistinctTraceHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctKeysHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.DistinctValuesHandler
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.core.trace.CallTraceInterpreter
import com.intellij.debugger.streams.lib.impl.BreakpointBasedIntermediateOperation
import com.intellij.debugger.streams.lib.impl.BreakpointBasedTerminalOperation
import com.intellij.debugger.streams.lib.impl.MatchingOperation
import com.intellij.debugger.streams.lib.impl.OptionalResultOperation
import com.intellij.debugger.streams.lib.impl.ParallelOperation
import com.intellij.debugger.streams.trace.breakpoint.ObjectStorage
import com.intellij.debugger.streams.core.trace.IntermediateCallHandler as DslIntermediateCallHandler
import com.sun.jdi.ObjectReference

class BreakpointBasedMappingOperation(name: String) : MappingOperation(name), BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler = PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
}

class BreakpointBasedFlatMappingOperation(name: String) : FlatMappingOperation(name), BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference
  ): IntermediateCallHandler = PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
}

class BreakpointBasedSortedOperation(name: String) : SortedOperation(name), BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference
  ): IntermediateCallHandler = PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
}

class BreakpointBasedFilterOperation(name: String) : FilterOperation(name), BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference
  ): IntermediateCallHandler = PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
}

class BreakpointBasedParallelOperation(name: String) : ParallelOperation(name), BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler = ParallelCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
}

class BreakpointBasedConcatOperation(name: String, resolver: ValuesOrderResolver)
  : ConcatOperation(name, resolver), BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler = PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
}

class BreakpointBasedCollapseOperation(name: String)
  : CollapseOperation(name), BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler = PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
}

class BreakpointBasedOrderBasedOperation(name: String, resolver: ValuesOrderResolver)
  : OrderBasedOperation(name, resolver), BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler = PeekCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
}

class BreakpointBasedDistinctOperation(name: String)
  : DistinctOperation(name, { num, call, dsl ->
      val args = call.arguments
      if (args.isEmpty() || args[0].type == "int") DistinctTraceHandler(num, call, dsl)
      else DistinctByKeyHandler(num, call, dsl)
    }),
    BreakpointBasedIntermediateOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler {
    val args = call.arguments
    return if (args.isEmpty() || args[0].type == "int")
      DistinctCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
    else
      DistinctByKeyCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time)
  }
}

class BreakpointBasedDistinctMapEntryOperation private constructor(
  name: String,
  dslHandlerFactory: (Int, IntermediateStreamCall, Dsl) -> DslIntermediateCallHandler,
  private val utilsMethodName: String,
) : DistinctOperation(name, dslHandlerFactory), BreakpointBasedIntermediateOperation {

  companion object {
    fun keys(name: String): BreakpointBasedDistinctMapEntryOperation = BreakpointBasedDistinctMapEntryOperation(
      name, ::DistinctKeysHandler, "computeDistinctByMapKeyMapping")
    fun values(name: String): BreakpointBasedDistinctMapEntryOperation = BreakpointBasedDistinctMapEntryOperation(
      name, ::DistinctValuesHandler, "computeDistinctByMapValueMapping")
  }

  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    callOrder: Int,
    call: IntermediateStreamCall,
    time: ObjectReference,
  ): IntermediateCallHandler =
    DistinctByMapEntryCallHandler(objectStorage, call.getTypeBefore(), call.getTypeAfter(), time, utilsMethodName)
}

class BreakpointBasedToCollectionOperation(name: String) : ToCollectionOperation(name), BreakpointBasedTerminalOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    call: TerminatorStreamCall,
    time: ObjectReference,
  ): TerminalCallHandler = ToCollectionTerminalCallHandler(call, objectStorage, time)
}

class BreakpointBasedMatchingOperation(name: String, interpreter: CallTraceInterpreter) : MatchingOperation(name, interpreter), BreakpointBasedTerminalOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    call: TerminatorStreamCall,
    time: ObjectReference,
  ): TerminalCallHandler = MatchRuntimeHandler(call, objectStorage, time)
}

class BreakpointBasedOptionalResultOperation(name: String) : OptionalResultOperation(name), BreakpointBasedTerminalOperation {
  override fun getRuntimeTraceHandler(
    objectStorage: ObjectStorage,
    call: TerminatorStreamCall,
    time: ObjectReference,
  ): TerminalCallHandler = OptionalRuntimeHandler(call, objectStorage, time)
}