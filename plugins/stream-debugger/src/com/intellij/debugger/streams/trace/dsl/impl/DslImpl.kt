// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl

import com.intellij.debugger.streams.trace.dsl.*
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
class DslImpl(private val statementFactory: StatementFactory) : Dsl {
  override val nullExpression: Expression = TextExpression("null")

  override val thisExpression: Expression = TextExpression("this")

  override val types: Types = statementFactory.types

  override fun variable(type: GenericType, name: String): Variable = statementFactory.createVariable(type, name)

  override fun code(init: CodeContext.() -> Unit): String {
    val fragment = MyContext()
    fragment.init()
    return fragment.toCode(0)
  }

  override fun block(init: CodeContext.() -> Unit): CodeBlock {
    val fragment = MyContext()
    fragment.init()
    return fragment
  }

  override fun array(elementType: GenericType, name: String): ArrayVariable = statementFactory.createArrayVariable(elementType, name)

  override fun newArray(elementType: GenericType, vararg args: Expression): Expression =
    statementFactory.createNewArrayExpression(elementType, args)

  override fun newSizedArray(elementType: GenericType, size: Expression): Expression =
    statementFactory.createNewSizedArray(elementType, size)

  override fun map(keyType: GenericType, valueType: GenericType, name: String): MapVariable =
    statementFactory.createMapVariable(keyType, valueType, name, false)

  override fun list(elementType: GenericType, name: String): ListVariable =
    statementFactory.createListVariable(elementType, name)

  override fun newList(elementType: GenericType, vararg args: Expression): Expression =
    statementFactory.createNewListExpression(elementType, *args)

  override fun linkedMap(keyType: GenericType, valueType: GenericType, name: String): MapVariable =
    statementFactory.createMapVariable(keyType, valueType, name, true)

  override fun lambda(argName: String, init: LambdaBody.(Expression) -> Unit): Lambda {
    val lambdaBody = statementFactory.createEmptyLambdaBody(argName)
    lambdaBody.init(argName.expr)
    return statementFactory.createLambda(argName, lambdaBody)
  }

  override fun declaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration =
    statementFactory.createVariableDeclaration(variable, init, isMutable)

  override fun timeDeclaration(): VariableDeclaration = statementFactory.createTimeVariableDeclaration()

  override fun currentTime(): Expression = statementFactory.currentTimeExpression()

  override fun updateTime(): Expression = statementFactory.updateCurrentTimeExpression()

  override fun createPeekCall(elementsType: GenericType, lambda: String): IntermediateStreamCall =
    statementFactory.createPeekCall(elementsType, lambda)

  override fun Expression.and(right: Expression): Expression = statementFactory.and(this, right)

  override fun Expression.equals(right: Expression): Expression = statementFactory.equals(this, right)

  override fun Expression.same(right: Expression): Expression = statementFactory.same(this, right)

  override fun Expression.not(): Expression = statementFactory.not(this)

  private inner class MyContext : CodeContext, Dsl by DslImpl@ this, CodeBlock by statementFactory.createEmptyCompositeCodeBlock()
}