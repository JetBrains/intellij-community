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
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.*
import com.intellij.debugger.streams.trace.dsl.impl.AssignmentStatement
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.dsl.impl.VariableImpl
import com.intellij.debugger.streams.trace.impl.handler.PeekCall
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
class JavaStatementFactory : StatementFactory {
  override val types: Types = JavaTypes()
  override fun createEmptyCompositeCodeBlock(): CompositeCodeBlock = JavaCodeBlock(this)

  override fun createEmptyCodeBlock(): CodeBlock = JavaCodeBlock(this)

  override fun createVariableDeclaration(variable: Variable, isMutable: Boolean): VariableDeclaration =
    JavaVariableDeclaration(variable, isMutable, Expression.Empty)

  override fun createVariableDeclaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration =
    JavaVariableDeclaration(variable, isMutable, init)

  override fun createEmptyForLoopBody(iterateVariable: Variable): ForLoopBody = JavaForLoopBody(this, iterateVariable)

  override fun createForEachLoop(iterateVariable: Variable, collection: Expression, loopBody: ForLoopBody): Convertable =
    JavaForEachLoop(iterateVariable, collection, loopBody)

  override fun createForLoop(initialization: VariableDeclaration,
                             condition: Expression,
                             afterThought: Expression,
                             loopBody: ForLoopBody): Convertable =
    JavaForLoop(initialization, condition, afterThought, loopBody)

  override fun createEmptyLambdaBody(argName: String): LambdaBody = JavaLambdaBody(this, argName)

  override fun createLambda(argName: String, lambdaBody: LambdaBody): Lambda {
    assert(lambdaBody is JavaLambdaBody)
    return JavaLambda(argName, lambdaBody as JavaLambdaBody)
  }

  override fun createVariable(type: GenericType, name: String): Variable = VariableImpl(type, name)

  override fun and(left: Expression, right: Expression): Expression = TextExpression("${left.toCode()} && ${right.toCode()}")

  override fun equals(left: Expression, right: Expression): Expression =
    TextExpression("java.util.Objects.equals(${left.toCode()}, ${right.toCode()}")

  override fun same(left: Expression, right: Expression): Expression = TextExpression("${left.toCode()} == ${right.toCode()}")

  override fun createIfBranch(condition: Expression, thenBlock: CodeBlock): IfBranch = JavaIfBranch(condition, thenBlock, this)

  override fun createAssignmentStatement(variable: Variable, expression: Expression): AssignmentStatement =
    JavaAssignmentStatement(variable, expression)

  override fun createMapVariable(keyType: GenericType, valueType: GenericType, name: String, linked: Boolean): MapVariable =
    JavaMapVariable(if (linked) types.linkedMap(keyType, valueType) else types.map(keyType, valueType), name)

  override fun createArrayVariable(elementType: GenericType, name: String): ArrayVariable =
    JavaArrayVariable(types.array(elementType), name)

  override fun createScope(codeBlock: CodeBlock): Convertable = object : Convertable {
    override fun toCode(indent: Int): String = "{\n".withIndent(indent) +
                                               codeBlock.toCode(indent + 1) +
                                               "}".withIndent(indent)
  }

  override fun createTryBlock(block: CodeBlock): TryBlock = JavaTryBlock(block, this)

  override fun createTimeVariableDeclaration(): VariableDeclaration =
    JavaVariableDeclaration(createVariable(types.timeVariableType, "time"), false,
                            TextExpression(types.timeVariableType.defaultValue))

  override fun currentTimeExpression(): Expression = TextExpression("time").call("get")

  override fun updateCurrentTimeExpression(): Expression = TextExpression("time").call("incrementAndGet")

  override fun createNewArrayExpression(elementType: GenericType, vararg args: Expression): Expression {
    val elements = args.joinToString(separator = ", ") { it.toCode() }
    return TextExpression("new ${elementType.variableTypeName}[] { $elements }")
  }

  override fun createNewSizedArray(elementType: GenericType, size: Expression): Expression =
    TextExpression("new ${elementType.variableTypeName}[${size.toCode()}]")

  override fun createNewListExpression(elementType: GenericType, vararg args: Expression): Expression {
    if (args.isEmpty()) {
      return TextExpression(types.list(elementType).defaultValue)
    }

    return TextExpression("java.util.Arrays.asList(${args.joinToString(separator = ", ") { it.toCode() }})")
  }

  override fun createPeekCall(elementsType: GenericType, lambda: String): IntermediateStreamCall = PeekCall(lambda, elementsType)

  override fun createListVariable(elementType: GenericType, name: String): ListVariable = JavaListVariable(types.list(elementType), name)

  override fun not(expression: Expression): Expression = TextExpression("!${expression.toCode()}")
}
