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
import com.intellij.debugger.streams.trace.dsl.CodeBlock
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.Variable
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.streams.wrapper.impl.StreamChainImpl
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.util.collectionUtils.concat
import java.util.*

/**
 * @author Vitaliy.Bibaev
 */
abstract class TraceExpressionBuilderBase(private val myProject: Project, protected val dsl: Dsl) : TraceExpressionBuilder {
  protected val resultVariableName = "myRes"

  override fun createTraceExpression(chain: StreamChain): String {
    val libraryManager = LibraryManager.getInstance(myProject)
    val intermediateHandlers = getHandlers(libraryManager, chain.intermediateCalls)
    val terminatorCall = chain.terminationCall
    val handlerFactory = libraryManager.getLibrary(terminatorCall).createHandlerFactory(dsl)
    val terminatorHandler = handlerFactory.getForTermination(terminatorCall, "evaluationResult[0]")

    val traceChain = buildTraceChain(chain, intermediateHandlers, terminatorHandler)

    val infoArraySize = 2 + intermediateHandlers.size
    val info = dsl.array(dsl.types.anyType, "info")
    val streamResult = dsl.variable(dsl.types.anyType, "streamResult")
    val declarations = buildDeclarations(intermediateHandlers, terminatorHandler)

    val tracingCall = buildStreamExpression(traceChain, streamResult)
    val fillingInfoArray = buildFillInfo(intermediateHandlers, terminatorHandler, info)

    val result = dsl.variable(dsl.types.anyType, resultVariableName)

    return dsl.code {
      scope {
        // TODO: avoid language dependent code
        val startTime = declare(variable(types.longType, "startTime"), +"java.lang.System.nanoTime()", false)
        declare(info, newSizedArray(types.anyType, infoArraySize), false)
        declare(timeDeclaration())
        add(declarations)
        add(tracingCall)
        add(fillingInfoArray)

        val elapsedTime = declare(array(types.longType, "elapsedTime"),
                                  newArray(types.longType, +"java.lang.System.nanoTime() - ${startTime.toCode()}"), false)
        result.assign(newArray(types.anyType, info, streamResult, elapsedTime))
      }
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
    val lambda = dsl.lambda("x") {
      +dsl.updateTime()
    }.toCode()

    return dsl.createPeekCall(elementType, lambda)
  }

  private fun buildDeclarations(intermediateCallsHandlers: List<IntermediateCallHandler>,
                                terminatorHandler: TerminatorCallHandler): CodeBlock {
    return dsl.block {
      intermediateCallsHandlers.flatMap { it.additionalVariablesDeclaration() }
        .concat(terminatorHandler.additionalVariablesDeclaration())!!
        .forEach({ declare(it) })
    }
  }

  private fun buildStreamExpression(chain: StreamChain, streamResult: Variable): CodeBlock {
    val resultType = chain.terminationCall.resultType
    return dsl.block {
      declare(streamResult, nullExpression, true)
      tryBlock {
        if (resultType === GenericType.VOID) {
          streamResult.assign(newSizedArray(types.anyType, 1))
        }
        else {
          val evaluationResult = array(resultType, "evaluationResult")
          declare(evaluationResult, newArray(resultType, TextExpression(resultType.defaultValue)), true)
          +evaluationResult.set(0, TextExpression(chain.text))
          streamResult.assign(evaluationResult)
        }
      }.catch(variable(types.basicExceptionType, "t")) {
        // TODO: add exception variable as a property of catch code block
        streamResult.assign(newArray(types.basicExceptionType, +"t"))
      }
    }
  }

  private fun buildFillInfo(intermediateCallsHandlers: List<IntermediateCallHandler>,
                            terminatorHandler: TerminatorCallHandler,
                            info: ArrayVariable): CodeBlock {
    val handlers = listOf<TraceHandler>(*intermediateCallsHandlers.toTypedArray(), terminatorHandler)
    return dsl.block {
      for ((i, handler) in handlers.withIndex()) {
        scope {
          add(handler.prepareResult())
          +info.set(i, handler.resultExpression)
        }
      }
    }
  }

  private fun getHandlers(libraryManager: LibraryManager,
                          intermediateCalls: List<IntermediateStreamCall>): List<IntermediateCallHandler> =
    intermediateCalls.mapIndexedTo(ArrayList()) { i, call ->
      libraryManager.getLibrary(call).createHandlerFactory(dsl).getForIntermediate(i, call)
    }
}