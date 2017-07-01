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
package com.intellij.debugger.streams.trace.impl.handler

import com.intellij.debugger.streams.trace.impl.TraceExpressionBuilderImpl.LINE_SEPARATOR
import com.intellij.debugger.streams.trace.impl.handler.HandlerBase.Intermediate
import com.intellij.debugger.streams.trace.impl.handler.type.ClassTypeImpl
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.CallArgument
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.impl.CallArgumentImpl
import com.intellij.debugger.streams.wrapper.impl.IntermediateStreamCallImpl

/**
 * @author Vitaliy.Bibaev
 */
class DistinctByKeyHandler(callNumber: Int, call: IntermediateStreamCall) : Intermediate() {
  private companion object {
    val KEY_EXTRACTOR_VARIABLE_PREFIX = "keyExtractor"
  }

  private val myPeekHandler: PeekTracerHandler = PeekTracerHandler(callNumber, "distinct", call.typeBefore, call.typeAfter)
  private val myKeyExtractor: CallArgument
  private val myTypeAfter = call.typeAfter
  private val myVariableName: String = KEY_EXTRACTOR_VARIABLE_PREFIX + callNumber
  private val myBeforeTimes = ListVariableImpl(call.name + callNumber + "BeforeTimes", GenericType.INT)
  private val myBeforeValues = ListVariableImpl(call.name + callNumber + "BeforeValues", GenericType.OBJECT)
  private val myKeys = ListVariableImpl(call.name + callNumber + "Keys", GenericType.OBJECT)
  private val myTime2ValueAfter = HashMapVariableImpl(call.name + callNumber + "after", GenericType.INT, GenericType.OBJECT, true)

  init {
    val arguments = call.arguments
    assert(arguments.isNotEmpty())
    myKeyExtractor = arguments.first()
  }

  override fun additionalCallsBefore(): MutableList<IntermediateStreamCall> = myPeekHandler.additionalCallsBefore()


  override fun getVariables(): MutableList<Variable> {
    val variable = VariableImpl(myKeyExtractor.type, myVariableName, myKeyExtractor.text)
    val variables = mutableListOf<Variable>(variable, myBeforeTimes, myBeforeValues, myTime2ValueAfter, myKeys)
    variables.addAll(myPeekHandler.variables)

    return variables
  }

  override fun transformCall(call: IntermediateStreamCall): IntermediateStreamCall {
    return call.updateArguments(
      listOf(CallArgumentImpl(myKeyExtractor.type,
                              "x -> $myVariableName.andThen(t -> {" + LINE_SEPARATOR +
                              "  ${myBeforeTimes.name}.add(time.get());" + LINE_SEPARATOR +
                              "  ${myBeforeValues.name}.add(x);" + LINE_SEPARATOR +
                              "  ${myKeys.name}.add(t);" + LINE_SEPARATOR +
                              "  return t; " + LINE_SEPARATOR +
                              "})" + LINE_SEPARATOR +
                              ".apply(x)")))
  }

  override fun additionalCallsAfter(): MutableList<IntermediateStreamCall> {
    val result = mutableListOf<IntermediateStreamCall>(PeekCall("x -> ${myTime2ValueAfter.name}.put(time.get(), x)", myTypeAfter))
    result.addAll(myPeekHandler.additionalCallsAfter())

    return result
  }

  override fun prepareResult(): String {
    val peekPrepare = myPeekHandler.prepareResult()

    val ifAbsent = "k -> new java.util.ArrayList<Integer>()"
    val keys2TimesBefore = HashMapVariableImpl("keys2Times", GenericType.OBJECT, ClassTypeImpl("java.util.List<Integer>"), false)
    val buildMap = "for (int i = 0; i < ${myKeys.name}.size(); i++) {" + LINE_SEPARATOR +
                   "  final Object key = ${myKeys.name}.get(i); " + LINE_SEPARATOR +
                   "  ${keys2TimesBefore.name}.computeIfAbsent(key, $ifAbsent).add(${myBeforeTimes.name}.get(i));" +
                   "}" + LINE_SEPARATOR

    val valuesAfterMapName = myTime2ValueAfter.name
    val transitions = HashMapVariableImpl("transitionsMap", GenericType.INT, GenericType.INT, false)
    val buildTransitions = "final boolean[] visited = new boolean[${myKeys.name}.size()];" + LINE_SEPARATOR +
                           "for(final int afterTime : $valuesAfterMapName.keySet()) {" + LINE_SEPARATOR +
                           "  Object valueAfter = $valuesAfterMapName.get(afterTime);" + LINE_SEPARATOR +
                           "  Object key = null;" + LINE_SEPARATOR +
                           "  for (int i = 0; i < visited.length; i++) {" + LINE_SEPARATOR +
                           "    if (!visited[i] && valueAfter == ${myBeforeValues.name}.get(i)) {" + LINE_SEPARATOR +
                           "      key = ${myKeys.name}.get(i); " + LINE_SEPARATOR +
                           "      visited[i] = true;" + LINE_SEPARATOR +
                           "      break;" + LINE_SEPARATOR +
                           "    }" + LINE_SEPARATOR +
                           "  }" + LINE_SEPARATOR +
                           "assert key != null;" + LINE_SEPARATOR +
                           "  for(final int beforeTime : ${keys2TimesBefore.name}.get(key)) {" + LINE_SEPARATOR +
                           "    ${transitions.name}.put(beforeTime, afterTime);" +
                           "  }" + LINE_SEPARATOR +
                           "}" + LINE_SEPARATOR

    val transitionsToArray = transitions.convertToArray("transitionsArray")
    return peekPrepare +
           Variable.declarationStatement(keys2TimesBefore) +
           Variable.declarationStatement(transitions) +
           buildMap +
           buildTransitions +
           transitionsToArray
  }

  override fun getResultExpression(): String {
    return "new Object[] { ${myPeekHandler.resultExpression}, transitionsArray }"
  }

  private fun IntermediateStreamCall.updateArguments(args: List<CallArgument>): IntermediateStreamCall {
    return IntermediateStreamCallImpl(name, args, typeBefore, typeAfter, textRange)
  }
}