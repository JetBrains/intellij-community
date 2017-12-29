// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified

import com.intellij.debugger.streams.trace.dsl.CodeBlock
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.ClassTypeImpl
import com.intellij.debugger.streams.wrapper.CallArgument
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.intellij.debugger.streams.wrapper.impl.CallArgumentImpl
import com.intellij.debugger.streams.wrapper.impl.IntermediateStreamCallImpl
import com.intellij.debugger.streams.wrapper.impl.TerminatorStreamCallImpl
import com.intellij.openapi.util.TextRange

/**
 * @author Vitaliy.Bibaev
 */
class MatchHandler(private val call: TerminatorStreamCall, dsl: Dsl) : HandlerBase.Terminal(dsl) {
  private companion object {
    val PREDICATE_NAME = "predicate42"
  }

  private val myPeekHandler = PeekTraceHandler(0, "filterMatch", call.typeBefore, call.typeBefore, dsl)
  private val myPredicateVariable = dsl.variable(ClassTypeImpl(call.arguments.first().type), PREDICATE_NAME)
  override fun additionalVariablesDeclaration(): List<VariableDeclaration> {
    val variables: MutableList<VariableDeclaration> = mutableListOf()
    variables.addAll(myPeekHandler.additionalVariablesDeclaration())
    val predicate = call.arguments.first()
    // TODO: store GenericType in CallArgument?
    variables.add(dsl.declaration(myPredicateVariable, TextExpression(predicate.text), false))

    return variables
  }

  override fun prepareResult(): CodeBlock {
    return dsl.block {
      val result = array(types.ANY, "result")
      declare(result, newSizedArray(types.ANY, 2), false)
      scope {
        add(myPeekHandler.prepareResult())
        statement { result.set(0, myPeekHandler.resultExpression) }
      }
      // TODO: avoid strange string literals in code (what is streamResult here ?!)
      statement { result.set(1, TextExpression("streamResult")) }
    }
  }

  override fun transformCall(call: TerminatorStreamCall): TerminatorStreamCall {
    val args = call.arguments
    assert(args.size == 1, { "Only predicate should be specified" })
    val predicate = args.first()
    val newPredicateBody = if (call.name == "allMatch") "false" else "true"
    val newPredicate = dsl.lambda("x") { doReturn(TextExpression(newPredicateBody)) }.toCode()
    return call.transformArgs(listOf(CallArgumentImpl(predicate.type, newPredicate)))
  }

  override fun getResultExpression(): Expression = TextExpression("result")

  override fun additionalCallsBefore(): List<IntermediateStreamCall> {
    val result = ArrayList(myPeekHandler.additionalCallsBefore())
    val filterPredicate = (if (call.name == "allMatch") myPredicateVariable.call("negate") else myPredicateVariable).toCode()
    val filterArg = CallArgumentImpl(myPredicateVariable.type.variableTypeName, filterPredicate)
    result += IntermediateStreamCallImpl("filter", listOf(filterArg), call.typeBefore, call.typeBefore, TextRange.EMPTY_RANGE)
    result.addAll(myPeekHandler.additionalCallsAfter())
    return result
  }

  private fun TerminatorStreamCall.transformArgs(args: List<CallArgument>): TerminatorStreamCall =
    TerminatorStreamCallImpl(name, args, typeBefore, resultType, textRange)
}