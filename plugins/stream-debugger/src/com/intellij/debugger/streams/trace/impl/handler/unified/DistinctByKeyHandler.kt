// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.trace.dsl.*
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.ClassTypeImpl
import com.intellij.debugger.streams.wrapper.CallArgument
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.impl.CallArgumentImpl
import com.intellij.debugger.streams.wrapper.impl.IntermediateStreamCallImpl
import com.intellij.openapi.util.TextRange
import one.util.streamex.StreamEx

/**
 * @author Vitaliy.Bibaev
 */
open class DistinctByKeyHandler(callNumber: Int,
                                private val myCall: IntermediateStreamCall,
                                dsl: Dsl,
                                private val functionApplyName: String = "apply") : HandlerBase.Intermediate(dsl) {
  private companion object {
    const val KEY_EXTRACTOR_VARIABLE_PREFIX = "keyExtractor"
    const val TRANSITIONS_ARRAY_NAME = "transitionsArray"
  }

  private val myPeekHandler = PeekTraceHandler(callNumber, "distinct", myCall.typeBefore, myCall.typeAfter, dsl)
  private val myKeyExtractor: CallArgument
  private val myTypeAfter = myCall.typeAfter
  private val myExtractorVariable: Variable
  private val myBeforeTimes = dsl.list(dsl.types.INT, myCall.name + callNumber + "BeforeTimes")
  private val myBeforeValues = dsl.list(dsl.types.ANY, myCall.name + callNumber + "BeforeValues")
  private val myKeys = dsl.list(dsl.types.ANY, myCall.name + callNumber + "Keys")
  private val myTime2ValueAfter = dsl.linkedMap(dsl.types.INT, dsl.types.ANY, myCall.name + callNumber + "after")

  init {
    val arguments = myCall.arguments
    assert(arguments.isNotEmpty(), { "Key extractor is not specified" })
    myKeyExtractor = arguments.first()
    myExtractorVariable = dsl.variable(ClassTypeImpl(myKeyExtractor.type), KEY_EXTRACTOR_VARIABLE_PREFIX + callNumber)
  }

  override fun additionalVariablesDeclaration(): List<VariableDeclaration> {
    val extractor = dsl.declaration(myExtractorVariable, TextExpression(myKeyExtractor.text), false)
    val variables =
      mutableListOf(extractor, myBeforeTimes.defaultDeclaration(), myBeforeValues.defaultDeclaration(),
                    myTime2ValueAfter.defaultDeclaration(), myKeys.defaultDeclaration())
    variables.addAll(myPeekHandler.additionalVariablesDeclaration())

    return variables
  }

  override fun transformCall(call: IntermediateStreamCall): IntermediateStreamCall {
    val newKeyExtractor = dsl.lambda("x") {
      val key = dsl.variable(dsl.types.ANY, "key")
      declare(key, myExtractorVariable.call(functionApplyName, lambdaArg), false)
      statement { myBeforeTimes.add(dsl.currentTime()) }
      statement { myBeforeValues.add(lambdaArg) }
      statement { myKeys.add(key) }
      doReturn(key)
    }.toCode()
    return call.updateArguments(listOf(CallArgumentImpl(myKeyExtractor.type, newKeyExtractor)))
  }

  override fun prepareResult(): CodeBlock {
    val keys2TimesBefore = dsl.map(dsl.types.ANY, dsl.types.list(dsl.types.INT), "keys2Times")
    val transitions = dsl.map(dsl.types.INT, dsl.types.INT, "transitionsMap")
    StreamEx.of(1).distinct().toList()
    return dsl.block {
      add(myPeekHandler.prepareResult())
      declare(keys2TimesBefore.defaultDeclaration())
      declare(transitions.defaultDeclaration())

      integerIteration(myKeys.size(), block@ this) {
        val key = declare(variable(types.ANY, "key"), myKeys.get(loopVariable), false)
        val lst = list(dsl.types.INT, "lst")
        declare(lst, keys2TimesBefore.computeIfAbsent(key, lambda("k") {
          doReturn(newList(types.INT))
        }), false)
        statement { lst.add(myBeforeTimes.get(loopVariable)) }
      }

      forEachLoop(variable(types.INT, "afterTime"), myTime2ValueAfter.keys()) {
        val afterTime = loopVariable
        val valueAfter = declare(variable(types.ANY, "valueAfter"), myTime2ValueAfter.get(loopVariable), false)
        val key = declare(variable(types.ANY, "key"), nullExpression, true)
        integerIteration(myBeforeTimes.size(), forEachLoop@ this) {
          ifBranch((valueAfter same myBeforeValues.get(loopVariable)) and !transitions.contains(myBeforeTimes.get(loopVariable))) {
            key assign myKeys.get(loopVariable)
            statement { breakIteration() }
          }
        }

        forEachLoop(variable(types.INT, "beforeTime"), keys2TimesBefore.get(key)) {
          statement { transitions.set(loopVariable, afterTime) }
        }
      }

      add(transitions.convertToArray(this, "transitionsArray"))
    }
  }

  override fun getResultExpression(): Expression =
    dsl.newArray(dsl.types.ANY, myPeekHandler.resultExpression, TextExpression(TRANSITIONS_ARRAY_NAME))

  override fun additionalCallsBefore(): List<IntermediateStreamCall> = myPeekHandler.additionalCallsBefore()

  override fun additionalCallsAfter(): List<IntermediateStreamCall> {
    val callsAfter = ArrayList(myPeekHandler.additionalCallsAfter())
    val lambda = dsl.lambda("x") {
      doReturn(myTime2ValueAfter.set(dsl.currentTime(), lambdaArg))
    }

    callsAfter.add(dsl.createPeekCall(myTypeAfter, lambda))
    return callsAfter
  }

  private fun CodeContext.integerIteration(border: Expression, block: CodeBlock, init: ForLoopBody.() -> Unit) {
    block.forLoop(declaration(variable(types.INT, "i"), TextExpression("0"), true),
                  TextExpression("i < ${border.toCode()}"),
                  TextExpression("i = i + 1"), init)
  }

  private fun IntermediateStreamCall.updateArguments(args: List<CallArgument>): IntermediateStreamCall =
    IntermediateStreamCallImpl(myCall.name, args, typeBefore, typeAfter, textRange)

  open class DistinctByCustomKey(callNumber: Int,
                                 call: IntermediateStreamCall,
                                 extractorType: String,
                                 extractorExpression: String,
                                 dsl: Dsl)
    : DistinctByKeyHandler(callNumber, call.transform(extractorType, extractorExpression), dsl) {

    private companion object {
      fun IntermediateStreamCall.transform(extractorType: String, extractorExpression: String): IntermediateStreamCall =
        IntermediateStreamCallImpl("distinct", listOf(CallArgumentImpl(extractorType, extractorExpression)), typeBefore, typeAfter,
                                   TextRange.EMPTY_RANGE)

    }
  }
}

class DistinctKeysHandler(callNumber: Int, call: IntermediateStreamCall, dsl: Dsl)
  : DistinctByKeyHandler.DistinctByCustomKey(callNumber, call, "java.util.function.Function<java.util.Map.Entry, java.lang.Object>",
                                             dsl.lambda("x") {
                                               doReturn(lambdaArg.call("getKey"))
                                             }.toCode(),
                                             dsl)

class DistinctValuesHandler(callNumber: Int, call: IntermediateStreamCall, dsl: Dsl)
  : DistinctByKeyHandler.DistinctByCustomKey(callNumber, call, "java.util.function.Function<java.util.Map.Entry, java.lang.Object>",
                                             dsl.lambda("x") {
                                               doReturn(lambdaArg.call("getValue"))
                                             }.toCode(),
                                             dsl)
