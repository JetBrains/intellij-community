// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

/**
 * @author Vitaliy.Bibaev
 */
interface CodeBlock : Statement {
  val size: Int

  fun declare(variable: Variable, isMutable: Boolean): Variable

  fun declare(variable: Variable, init: Expression, isMutable: Boolean): Variable

  fun declare(declaration: VariableDeclaration): Variable

  fun forEachLoop(iterateVariable: Variable, collection: Expression, init: ForLoopBody.() -> Unit)

  fun forLoop(initialization: VariableDeclaration, condition: Expression, afterThought: Expression, init: ForLoopBody.() -> Unit)

  fun tryBlock(init: CodeBlock.() -> Unit): TryBlock

  fun scope(init: CodeBlock.() -> Unit)

  fun ifBranch(condition: Expression, init: CodeBlock.() -> Unit): IfBranch

  fun call(receiver: Expression, methodName: String, vararg args: Expression): Expression

  fun doReturn(expression: Expression)

  fun statement(statement: () -> Statement)

  operator fun Statement.unaryPlus()

  infix fun Variable.assign(expression: Expression)

  fun add(block: CodeBlock)

  fun getStatements(): List<Convertable>
}