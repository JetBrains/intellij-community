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
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.streams.lib.LibraryManager
import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.TerminatorCallHandler
import com.intellij.debugger.streams.trace.TraceExpressionBuilder
import com.intellij.debugger.streams.trace.TraceHandler
import com.intellij.debugger.streams.trace.dsl.ArrayVariable
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.Variable
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.streams.wrapper.impl.StreamChainImpl
import com.intellij.openapi.project.Project
import java.util.*

/**
 * @author Vitaliy.Bibaev
 */
abstract class TraceExpressionBuilderBase(private val myProject: Project, private val myDsl: Dsl) : TraceExpressionBuilder {
  override fun createTraceExpression(chain: StreamChain): String {
    val libraryManager = LibraryManager.getInstance(myProject)
    val intermediateHandlers = getHandlers(libraryManager, chain.intermediateCalls)
    val terminatorCall = chain.terminationCall
    val terminatorHandler = libraryManager.getLibrary(terminatorCall).handlerFactory
      .getForTermination(terminatorCall, "evaluationResult[0]")

    val traceChain = buildTraceChain(chain, intermediateHandlers, terminatorHandler)

    val infoArraySize = 2 + intermediateHandlers.size
    val info = myDsl.array("java.lang.Object", "info")
    val streamResult = myDsl.variable("java.lang.Object", "streamResult")
    val declarations = buildDeclarations(intermediateHandlers, terminatorHandler)

    val tracingCall = buildStreamExpression(traceChain, streamResult)
    val fillingInfoArray = buildFillInfo(intermediateHandlers, terminatorHandler, info)

    val result = myDsl.variable("java.lang.Object", "myRes")

    return myDsl.code {
      declare(result, nullExpression, true)
      val startTime = declare(variable("long", "startTime"), +"java.lang.System.nanoTime()", false)
      declare(info, newSizedArray("java.lang.Object", infoArraySize), false)
      declare(timeDeclaration())
      +TextExpression(declarations)
      +TextExpression(tracingCall)
      +TextExpression(fillingInfoArray)

      val elapsedTime = declare(array("long", "elapsedTime"), +"java.lang.System.nanoTime() - ${startTime.toCode()}", false)
      result.assign(newArray("java.lang.Object", info, streamResult, elapsedTime))
      +result
    }
  }

  private fun buildTraceChain(chain: StreamChain,
                              intermediateCallHandlers: List<IntermediateCallHandler>,
                              terminatorHandler: TerminatorCallHandler): StreamChain {
    val newIntermediateCalls = mutableListOf<IntermediateStreamCall>()

    val qualifierExpression = chain.qualifierExpression
    newIntermediateCalls.add(createTimePeekCall(qualifierExpression.typeAfter))

    val intermediateCalls = chain.intermediateCalls

    assert(intermediateCalls.size == intermediateCallHandlers.size)

    for ((call, handler) in intermediateCalls.zip(intermediateCallHandlers)) {
      newIntermediateCalls.addAll(handler.additionalCallsBefore())

      newIntermediateCalls.add(handler.transformCall(call))
      newIntermediateCalls.add(createTimePeekCall(call.typeAfter))

      newIntermediateCalls.addAll(handler.additionalCallsAfter())
    }

    newIntermediateCalls.addAll(terminatorHandler.additionalCallsBefore())
    val terminatorCall = terminatorHandler.transformCall(chain.terminationCall)

    return StreamChainImpl(qualifierExpression, newIntermediateCalls, terminatorCall, chain.context)
  }

  private fun createTimePeekCall(elementType: GenericType): IntermediateStreamCall {
    val lambda = myDsl.code {
      lambda("x") {
        updateTime()
      }
    }
    return myDsl.createPeekCall(elementType, lambda)
  }

  private fun buildDeclarations(intermediateCallsHandlers: List<IntermediateCallHandler>,
                                terminatorHandler: TerminatorCallHandler): String {
    return myDsl.code {
      intermediateCallsHandlers.forEach({ +TextExpression(it.additionalVariablesDeclaration()) })
      +TextExpression(terminatorHandler.additionalVariablesDeclaration())
    }
  }

  private fun buildStreamExpression(chain: StreamChain, streamResult: Variable): String {
    val resultType = chain.terminationCall.resultType
    return myDsl.code {
      declare(streamResult, nullExpression, true)
      tryBlock {
        if (resultType === GenericType.VOID) {
          streamResult.assign(newSizedArray("java.lang.Object", 1))
        }
        else {
          val evaluationResult = array(resultType.variableTypeName, "evaluationResult")
          declare(evaluationResult, newSizedArray(resultType.variableTypeName, 1), true)
          evaluationResult.set(0, TextExpression(chain.text))
          streamResult.assign(evaluationResult)
        }
      }.catch(variable("java.lang.Throwable", "t")) {
        // TODO: add exception variable as a property of catch code block
        streamResult.assign(newArray("java.lang.Throwable", +"t"))
      }
    }
  }

  private fun buildFillInfo(intermediateCallsHandlers: List<IntermediateCallHandler>,
                            terminatorHandler: TerminatorCallHandler,
                            info: ArrayVariable): String {
    val handlers = listOf<TraceHandler>(*intermediateCallsHandlers.toTypedArray(), terminatorHandler)
    return myDsl.code {
      for ((i, handler) in handlers.withIndex()) {
        scope {
          +TextExpression(handler.prepareResult())
          +info.set(i, TextExpression(handler.resultExpression))
        }
      }
    }
  }

  private fun getHandlers(libraryManager: LibraryManager,
                          intermediateCalls: List<IntermediateStreamCall>): List<IntermediateCallHandler> =
    intermediateCalls.mapIndexedTo(ArrayList()) { i, call -> libraryManager.getLibrary(call).handlerFactory.getForIntermediate(i, call) }
}