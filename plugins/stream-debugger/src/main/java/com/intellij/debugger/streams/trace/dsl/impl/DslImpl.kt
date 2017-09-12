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

  override fun variable(type: String, name: String): Variable = statementFactory.createVariable(type, name)

  override fun code(init: CodeContext.() -> Unit): String {
    val fragment = MyContext()
    fragment.init()
    return fragment.toCode(0)
  }

  override fun array(elementType: String, name: String): ArrayVariable = statementFactory.createArrayVariable(elementType, name)

  override fun newArray(elementType: String, vararg args: Expression): Expression =
    statementFactory.createNewArrayExpression(elementType, args)

  override fun newSizedArray(elementType: String, size: Expression): Expression = statementFactory.createNewSizedArray(elementType, size)

  override fun map(keyType: GenericType, valueType: GenericType, name: String): MapVariable =
    statementFactory.createMapVariable(keyType, valueType, name, false)


  override fun linkedMap(keyType: GenericType, valueType: GenericType, name: String): MapVariable =
    statementFactory.createMapVariable(keyType, valueType, name, true)

  override fun lambda(argName: String, init: LambdaBody.(Expression) -> Unit): Lambda {
    val lambdaBody = statementFactory.createEmptyLambdaBody(argName)
    lambdaBody.init(+argName)
    return statementFactory.createLambda(argName, lambdaBody)
  }

  override fun declaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration =
    statementFactory.createVariableDeclaration(variable, init, isMutable)

  override fun timeDeclaration(): VariableDeclaration = statementFactory.createTimeVariableDeclaration()

  override fun currentTime(): Expression = statementFactory.currentTimeExpression()

  override fun updateTime(): Expression = statementFactory.updateCurrentTimeExpression()

  override fun createPeekCall(elementsType: GenericType, lambda: String): IntermediateStreamCall =
    statementFactory.createPeekCall(elementsType, lambda)

  override fun String.unaryPlus(): TextExpression = TextExpression(this)

  override fun and(left: Expression, right: Expression): Expression = statementFactory.and(left, right)

  override fun equals(left: Expression, right: Expression): Expression = statementFactory.equals(left, right)

  override fun same(left: Expression, right: Expression): Expression = statementFactory.same(left, right)

  private inner class MyContext : CodeContext, Dsl by DslImpl@ this, CodeBlock by statementFactory.createEmptyCompositeCodeBlock()
}