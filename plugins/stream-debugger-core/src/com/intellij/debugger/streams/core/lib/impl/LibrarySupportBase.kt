// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.lib.impl

import com.intellij.debugger.streams.core.lib.*
import com.intellij.debugger.streams.core.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.core.trace.CallTraceInterpreter
import com.intellij.debugger.streams.core.trace.IntermediateCallHandler
import com.intellij.debugger.streams.core.trace.TerminatorCallHandler
import com.intellij.debugger.streams.core.trace.dsl.Dsl
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.StreamCallType
import com.intellij.debugger.streams.core.wrapper.TerminatorStreamCall

/**
 * @author Vitaliy.Bibaev
 */
abstract class LibrarySupportBase(private val compatibleLibrary: LibrarySupport = LibrarySupportBase.EMPTY) : LibrarySupport {
  companion object {
    val EMPTY: LibrarySupport = DefaultLibrarySupport()
  }

  private val mySupportedIntermediateOperations: MutableMap<String, IntermediateOperation> = mutableMapOf()
  private val mySupportedTerminalOperations: MutableMap<String, TerminalOperation> = mutableMapOf()

  final override fun createHandlerFactory(dsl: Dsl): HandlerFactory {
    val compatibleLibraryFactory = compatibleLibrary.createHandlerFactory(dsl)
    return object : HandlerFactory {
      override fun getForIntermediate(number: Int, call: IntermediateStreamCall): IntermediateCallHandler {
        val operation = mySupportedIntermediateOperations[call.name]
        return operation?.getTraceHandler(number, call, dsl)
               ?: compatibleLibraryFactory.getForIntermediate(number, call)
      }

      override fun getForTermination(call: TerminatorStreamCall, resultExpression: String): TerminatorCallHandler {
        val terminalOperation = mySupportedTerminalOperations[call.name]
        return terminalOperation?.getTraceHandler(call, resultExpression, dsl)
               ?: compatibleLibraryFactory.getForTermination(call, resultExpression)
      }
    }
  }

  final override val interpreterFactory: InterpreterFactory = object : InterpreterFactory {
    override fun getInterpreter(callName: String, callType: StreamCallType): CallTraceInterpreter {
      val operation = findOperation(callName, callType)
      return operation?.traceInterpreter
             ?: compatibleLibrary.interpreterFactory.getInterpreter(callName, callType)
    }
  }

  final override val resolverFactory: ResolverFactory = object : ResolverFactory {
    override fun getResolver(callName: String, callType: StreamCallType): ValuesOrderResolver {
      val operation = findOperation(callName, callType)
      return operation?.valuesOrderResolver
             ?: compatibleLibrary.resolverFactory.getResolver(callName, callType)
    }
  }

  protected fun addIntermediateOperationsSupport(vararg operations: IntermediateOperation) {
    operations.forEach { mySupportedIntermediateOperations[it.name] = it }
  }

  protected fun addTerminationOperationsSupport(vararg operations: TerminalOperation) {
    operations.forEach { mySupportedTerminalOperations[it.name] = it }
  }

  private fun findOperation(name: String, callType: StreamCallType): Operation? {
    return when (callType) {
      StreamCallType.INTERMEDIATE -> mySupportedIntermediateOperations[name]
      StreamCallType.TERMINATOR -> mySupportedTerminalOperations[name]
      else -> error("Unsupported call type: $callType for call: $name")
    }
  }
}