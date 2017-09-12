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

/**
 * @author Vitaliy.Bibaev
 */
interface CodeBlock : Statement {
  fun declare(variable: Variable, isMutable: Boolean): Variable

  fun declare(variable: Variable, init: Expression, isMutable: Boolean): Variable

  fun declare(declaration: VariableDeclaration): Variable

  fun forEachLoop(iterateVariable: Variable, collection: Expression, init: ForLoopBody.() -> Unit)

  fun forLoop(initialization: VariableDeclaration, condition: Expression, afterThought: Expression, init: ForLoopBody.() -> Unit)

  fun tryBlock(init: CodeBlock.() -> Unit): TryBlock

  fun scope(init: CodeBlock.() -> Unit)

  fun ifBranch(condition: Expression, init: CodeBlock.() -> Unit): IfBranch

  fun call(receiver: Expression, methodName: String, vararg args: Expression): Expression

  operator fun Variable.unaryPlus(): Variable

  operator fun Expression.unaryPlus()

  fun Variable.assign(expression: Expression)
}