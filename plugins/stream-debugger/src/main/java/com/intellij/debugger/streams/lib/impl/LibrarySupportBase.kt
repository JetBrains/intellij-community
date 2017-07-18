/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.diagnostic.ex.OperationNotSupportedException
import com.intellij.debugger.streams.lib.*
import com.intellij.debugger.streams.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.trace.CallTraceResolver
import com.intellij.debugger.streams.trace.TraceHandler
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall

/**
 * @author Vitaliy.Bibaev
 */
abstract class LibrarySupportBase(override val description: Library,
                                  private val compatibleLibrary: LibrarySupport = EmptyLibrarySupport()) : LibrarySupport {
  private val mySupportedIntermediateOperations: MutableMap<String, IntermediateOperation> = mutableMapOf()
  private val mySupportedTerminalOperations: MutableMap<String, TerminalOperation> = mutableMapOf()

  final override val handlerFactory: HandlerFactory = object : HandlerFactory {
    override fun getForIntermediate(number: Int, call: IntermediateStreamCall): TraceHandler {
      val operation = mySupportedIntermediateOperations[call.name]
      return operation?.getTraceHandler(number, call)
             ?: compatibleLibrary.handlerFactory.getForIntermediate(number, call)
    }

    override fun getForTermination(call: TerminatorStreamCall, resultExpression: String): TraceHandler {
      val terminalOperation = mySupportedTerminalOperations[call.name]
      return terminalOperation?.getTraceHandler(call, resultExpression)
             ?: compatibleLibrary.handlerFactory.getForTermination(call, resultExpression)
    }
  }

  final override val interpreterFactory: InterpreterFactory = object : InterpreterFactory {
    override fun getInterpreter(callName: String): CallTraceResolver {
      val operation = findOperationByName(callName)
      return operation?.traceInterpreter
             ?: compatibleLibrary.interpreterFactory.getInterpreter(callName)
    }
  }

  final override val resolverFactory: ResolverFactory = object : ResolverFactory {
    override fun getResolver(callName: String): ValuesOrderResolver {
      val operation = findOperationByName(callName)
      return operation?.valuesOrderResolver
             ?: compatibleLibrary.resolverFactory.getResolver(callName)
    }
  }

  protected fun addIntermediateOperationsSupport(vararg operations: IntermediateOperation): Unit {
    operations.forEach { mySupportedIntermediateOperations[it.name] = it }
  }

  protected fun addTerminationOperationsSupport(vararg operations: TerminalOperation): Unit {
    operations.forEach { mySupportedTerminalOperations[it.name] = it }
  }

  private fun findOperationByName(name: String): Operation? =
    mySupportedIntermediateOperations[name] ?: mySupportedTerminalOperations[name]

  private class EmptyLibrarySupport : LibrarySupport {
    override val description: Library
      get() {
        throw RuntimeException("There is no description for empty library")
      }

    override val handlerFactory: HandlerFactory = object : HandlerFactory {
      override fun getForIntermediate(number: Int, call: IntermediateStreamCall): TraceHandler {
        throw OperationNotSupportedException("Intermediate operation ${call.name} is not supported")
      }

      override fun getForTermination(call: TerminatorStreamCall, resultExpression: String): TraceHandler {
        throw OperationNotSupportedException("Terminal operation ${call.name} is not supported")
      }
    }
    override val interpreterFactory: InterpreterFactory = object : InterpreterFactory {
      override fun getInterpreter(callName: String): CallTraceResolver {
        throw OperationNotSupportedException("Operation $callName is not supported")
      }
    }
    override val resolverFactory: ResolverFactory = object : ResolverFactory {
      override fun getResolver(callName: String): ValuesOrderResolver {
        throw OperationNotSupportedException("Operation $callName is not supported")
      }
    }
  }
}