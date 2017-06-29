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
class DistinctByPredicateHandler(callNumber: Int, call: IntermediateStreamCall) : Intermediate() {
  private companion object {
    val KEY_EXTRACTOR_VARIABLE_PREFIX = "keyExtractor"
  }

  private val myPeekHandler: PeekTracerHandler = PeekTracerHandler(callNumber, "distinct", call.typeBefore, call.typeAfter)
  private val myKeyExtractor: CallArgument
  private val myTypeAfter = call.typeAfter
  private val myVariableName: String = KEY_EXTRACTOR_VARIABLE_PREFIX + callNumber
  private val myWrapperClassName = "Wrapper" + callNumber
  private val myUtilityMap = HashMapVariableImpl(call.name + callNumber + "utilityMap",
                                                 ClassTypeImpl(myWrapperClassName), GenericType.OBJECT, false)
  private val myValuesAfter = HashMapVariableImpl(call.name + callNumber + "after", GenericType.INT, GenericType.OBJECT, true)

  init {
    val arguments = call.arguments
    assert(arguments.isNotEmpty())
    myKeyExtractor = arguments.first()
  }

  override fun additionalCallsBefore(): MutableList<IntermediateStreamCall> = myPeekHandler.additionalCallsBefore()


  override fun getVariables(): MutableList<Variable> {
    val variable = VariableImpl(myKeyExtractor.type, myVariableName, myKeyExtractor.text)
    val variables = mutableListOf<Variable>(variable)
    variables.addAll(myPeekHandler.variables)

    return variables
  }

  override fun getClassesDeclarations(): MutableList<String> {
    return mutableListOf("class $myWrapperClassName { " + LINE_SEPARATOR +
                         "  private final Object myObj;" + LINE_SEPARATOR +
                         "  Wrapper(Object obj) { myObj = obj; }" + LINE_SEPARATOR +
                         "  public boolean equals(Object other) { return myObj == other; }" + LINE_SEPARATOR +
                         "  public Object get() { return myObj; }" + LINE_SEPARATOR +
                         "};" + LINE_SEPARATOR)
  }

  override fun transformCall(call: IntermediateStreamCall): IntermediateStreamCall {
    return call.updateArguments(
      listOf(CallArgumentImpl(myKeyExtractor.type,
                              "x -> $myVariableName.andThen(t -> {" +
                              "  ${myUtilityMap.name}.put(new $myWrapperClassName(x), t);" +
                              "  return t; " +
                              "})" +
                              ".apply(x)")))
  }

  override fun additionalCallsAfter(): MutableList<IntermediateStreamCall> {
    val result = mutableListOf<IntermediateStreamCall>(PeekCall("x -> $myValuesAfter.put(time.get(), x)", myTypeAfter))
    result.addAll(myPeekHandler.additionalCallsAfter())

    return result
  }

  override fun prepareResult(): String {
    val peekPrepare = myPeekHandler.prepareResult()

    return peekPrepare
  }

  override fun getResultExpression(): String {
    return ""
  }

  private fun IntermediateStreamCall.updateArguments(args: List<CallArgument>): IntermediateStreamCall {
    return IntermediateStreamCallImpl(name, args, typeBefore, typeAfter, textRange)
  }
}