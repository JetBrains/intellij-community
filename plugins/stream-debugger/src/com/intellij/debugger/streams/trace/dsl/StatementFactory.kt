// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.dsl.impl.AssignmentStatement
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * Contains language-dependent logic
 *
 * @author Vitaliy.Bibaev
 */
interface StatementFactory {
  companion object {
    fun commaSeparate(vararg args: Expression): String = args.joinToString(separator = ", ") { it.toCode() }
  }

  val types: Types

  fun createEmptyCompositeCodeBlock(): CompositeCodeBlock

  fun createEmptyCodeBlock(): CodeBlock

  fun createVariableDeclaration(variable: Variable, isMutable: Boolean): VariableDeclaration

  fun createVariableDeclaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration

  fun createEmptyForLoopBody(iterateVariable: Variable): ForLoopBody

  fun createForEachLoop(iterateVariable: Variable, collection: Expression, loopBody: ForLoopBody): Convertable

  fun createForLoop(initialization: VariableDeclaration,
                    condition: Expression,
                    afterThought: Expression,
                    loopBody: ForLoopBody): Convertable

  fun createEmptyLambdaBody(argName: String): LambdaBody

  fun createLambda(argName: String, lambdaBody: LambdaBody): Lambda

  fun createVariable(type: GenericType, name: String): Variable

  fun and(left: Expression, right: Expression): Expression

  fun equals(left: Expression, right: Expression): Expression

  fun same(left: Expression, right: Expression): Expression

  fun createIfBranch(condition: Expression, thenBlock: CodeBlock): IfBranch

  fun createAssignmentStatement(variable: Variable, expression: Expression): AssignmentStatement

  fun createMapVariable(keyType: GenericType, valueType: GenericType, name: String, linked: Boolean): MapVariable

  fun createArrayVariable(elementType: GenericType, name: String): ArrayVariable

  fun createScope(codeBlock: CodeBlock): Convertable

  fun createTryBlock(block: CodeBlock): TryBlock

  fun createTimeVariableDeclaration(): VariableDeclaration

  fun currentTimeExpression(): Expression

  fun updateCurrentTimeExpression(): Expression

  fun createNewArrayExpression(elementType: GenericType, args: Array<out Expression>): Expression

  fun createNewSizedArray(elementType: GenericType, size: Expression): Expression

  fun createNewListExpression(elementType: GenericType, vararg args: Expression): Expression

  fun createPeekCall(elementsType: GenericType, lambda: String): IntermediateStreamCall

  fun createListVariable(elementType: GenericType, name: String): ListVariable

  fun not(expression: Expression): Expression
}
