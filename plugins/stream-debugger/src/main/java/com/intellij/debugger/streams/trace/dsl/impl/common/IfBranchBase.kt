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
    elseBlock = elseIfStatement
    return elseIfStatement
  }
}