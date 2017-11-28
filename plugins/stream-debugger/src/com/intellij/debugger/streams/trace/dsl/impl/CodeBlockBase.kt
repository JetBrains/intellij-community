// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl

import com.intellij.debugger.streams.trace.dsl.*

/**
 * @author Vitaliy.Bibaev
 */
abstract class CodeBlockBase(private val myFactory: StatementFactory) : CompositeCodeBlock {
  private val myStatements: MutableList<Convertable> = mutableListOf()
  override val size: Int
    get() = myStatements.size

  override fun Statement.unaryPlus() {
    myStatements.add(this)
  }

  override fun statement(statement: () -> Statement) {
    myStatements.add(statement.invoke())
  }

  override fun declare(variable: Variable, isMutable: Boolean): Variable =
    declare(myFactory.createVariableDeclaration(variable, isMutable))

  override fun declare(variable: Variable, init: Expression, isMutable: Boolean): Variable =
    declare(myFactory.createVariableDeclaration(variable, init, isMutable))

  override fun declare(declaration: VariableDeclaration): Variable {
    addStatement(declaration)
    return declaration.variable
  }

  override fun forLoop(initialization: VariableDeclaration, condition: Expression, afterThought: Expression, init: ForLoopBody.() -> Unit) {
    val loopBody = myFactory.createEmptyForLoopBody(initialization.variable)
    loopBody.init()
    addStatement(myFactory.createForLoop(initialization, condition, afterThought, loopBody))
  }

  override fun tryBlock(init: CodeBlock.() -> Unit): TryBlock {
    val codeBlock = myFactory.createEmptyCodeBlock()
    codeBlock.init()
    val tryBlock = myFactory.createTryBlock(codeBlock)
    myStatements.add(tryBlock)
    return tryBlock
  }

  override fun ifBranch(condition: Expression, init: CodeBlock.() -> Unit): IfBranch {
    val ifBody = myFactory.createEmptyCodeBlock()
    ifBody.init()
    val branch = myFactory.createIfBranch(condition, ifBody)
    addStatement(branch)
    return branch
  }

  override fun call(receiver: Expression, methodName: String, vararg args: Expression): Expression {
    val call = receiver.call(methodName, *args)
    addStatement(call)
    return call
  }

  override fun forEachLoop(iterateVariable: Variable, collection: Expression, init: ForLoopBody.() -> Unit) {
    val loopBody = myFactory.createEmptyForLoopBody(iterateVariable)
    loopBody.init()
    addStatement(myFactory.createForEachLoop(iterateVariable, collection, loopBody))
  }

  override fun scope(init: CodeBlock.() -> Unit) {
    val codeBlock = myFactory.createEmptyCodeBlock()
    codeBlock.init()
    addStatement(myFactory.createScope(codeBlock))
  }

  override fun Variable.assign(expression: Expression) {
    val assignmentStatement = myFactory.createAssignmentStatement(this, expression)
    addStatement(assignmentStatement)
  }

  override fun addStatement(statement: Convertable) {
    myStatements += statement
  }

  override fun getStatements(): List<Convertable> = ArrayList(myStatements)
}