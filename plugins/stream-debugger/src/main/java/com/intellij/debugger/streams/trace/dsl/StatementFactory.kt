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
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.dsl.impl.AssignmentStatement
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType

/**
 * Contains language-dependent logic
 *
 * @author Vitaliy.Bibaev
 */
interface StatementFactory {
  fun createEmptyCompositeCodeBlock(): CompositeCodeBlock

  fun createEmptyCodeBlock(): CodeBlock

  fun createVariableDeclaration(variable: Variable, isMutable: Boolean): VariableDeclaration

  fun createVariableDeclaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration

  fun createEmptyForLoopBody(iterateVariable: Variable): ForLoopBody

  fun createForEachLoop(iterateVariable: Variable, collection: Expression, loopBody: ForLoopBody): Statement

  fun createForLoop(initialization: VariableDeclaration, condition: Expression, afterThought: Expression, loopBody: ForLoopBody): Statement

  fun createEmptyLambdaBody(argName: String): LambdaBody

  fun createLambda(argName: String, lambdaBody: LambdaBody): Lambda

  fun createVariable(type: String, name: String): Variable

  fun and(left: Expression, right: Expression): Expression

  fun equals(left: Expression, right: Expression): Expression

  fun same(left: Expression, right: Expression): Expression

  fun createIfBranch(condition: Expression, thenBlock: CodeBlock): IfBranch

  fun createAssignmentStatement(variable: Variable, expression: Expression): AssignmentStatement

  fun createMapVariable(keyType: GenericType, valueType: GenericType, name: String, linked: Boolean): MapVariable

  fun createArrayVariable(elementType: String, name: String): ArrayVariable

  fun createScope(codeBlock: CodeBlock): Statement

  fun createTryBlock(block: CodeBlock): TryBlock
}
