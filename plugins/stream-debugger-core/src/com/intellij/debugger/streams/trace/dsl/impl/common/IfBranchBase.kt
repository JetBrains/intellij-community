// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.common

import com.intellij.debugger.streams.trace.dsl.*

/**
 * @author Vitaliy.Bibaev
 */
abstract class IfBranchBase(protected val condition: Expression,
                            protected val thenBlock: CodeBlock,
                            private val statementFactory: StatementFactory) : IfBranch {
  protected var elseBlock: Statement? = null
  override fun elseBranch(init: CodeBlock.() -> Unit) {
    val block = statementFactory.createEmptyCodeBlock()
    block.init()
    elseBlock = block
  }

  override fun elseIfBranch(condition: Expression, init: CodeBlock.() -> Unit): IfBranch {
    val block = statementFactory.createEmptyCodeBlock()
    block.init()
    val elseIfStatement = statementFactory.createIfBranch(condition, block)
    val codeBlock = statementFactory.createEmptyCompositeCodeBlock()
    codeBlock.addStatement(elseIfStatement)
    elseBlock = codeBlock
    return elseIfStatement
  }
}