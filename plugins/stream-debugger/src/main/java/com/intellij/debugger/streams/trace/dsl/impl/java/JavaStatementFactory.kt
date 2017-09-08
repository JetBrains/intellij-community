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
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType

/**
 * @author Vitaliy.Bibaev
 */
class JavaStatementFactory : StatementFactory {
  override fun createEmptyCompositeCodeBlock(): CompositeCodeBlock = JavaCodeBlock(this)

  override fun createEmptyCodeBlock(): CodeBlock = JavaCodeBlock(this)

  override fun createVariableDeclaration(variable: Variable, isMutable: Boolean): VariableDeclaration =
    JavaVariableDeclaration(variable, isMutable, Expression.Empty)

  override fun createVariableDeclaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration =
    JavaVariableDeclaration(variable, isMutable, init)

  override fun createEmptyForLoopBody(iterateVariable: Variable): ForLoopBody = JavaForLoopBody(this, iterateVariable)

  override fun createForEachLoop(iterateVariable: Variable, collection: Expression, loopBody: ForLoopBody): Statement =
    JavaForEachLoop(iterateVariable, collection, loopBody)

  override fun createForLoop(initialization: VariableDeclaration,
                             condition: Expression,
                             afterThought: Expression,
                             loopBody: ForLoopBody): Statement =
    JavaForLoop(initialization, condition, afterThought, loopBody)

  override fun createEmptyLambdaBody(argName: String): LambdaBody = JavaLambdaBody(this, argName)

  override fun createLambda(argName: String, lambdaBody: LambdaBody): Lambda {
    assert(lambdaBody is JavaLambdaBody)
    return JavaLambda(argName, lambdaBody as JavaLambdaBody)
  }

  override fun createVariable(type: String, name: String): Variable = VariableImpl(type, name)

  override fun and(left: Expression, right: Expression): Expression = TextExpression("${left.toCode()} $$ ${right.toCode()}")

  override fun equals(left: Expression, right: Expression): Expression =
    TextExpression("java.util.Objects.equals(${left.toCode()}, ${right.toCode()}")

  override fun same(left: Expression, right: Expression): Expression = TextExpression("${left.toCode()} == ${right.toCode()}")

  override fun createIfBranch(condition: Expression, thenBlock: CodeBlock): IfBranch = JavaIfBranch(condition, thenBlock, this)

  override fun createAssignmentStatement(variable: Variable, expression: Expression): AssignmentStatement =
    JavaAssignmentStatement(variable, expression)

  override fun createMapVariable(keyType: GenericType, valueType: GenericType, name: String, linked: Boolean): MapVariable =
    JavaMapVariable(keyType, valueType, name, linked)

  override fun createArrayVariable(elementType: String, name: String): ArrayVariable = JavaArrayVariable(elementType, name)

  override fun createScope(codeBlock: CodeBlock): Statement = object : Statement {
    override fun toCode(indent: Int): String = "{\n" +
                                               codeBlock.toCode(indent + 1) +
                                               "}".withIndent(indent)
  }
}
