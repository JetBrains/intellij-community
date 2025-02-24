// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.trace.dsl.impl.common

import com.intellij.debugger.streams.core.trace.dsl.CodeBlock
import com.intellij.debugger.streams.core.trace.dsl.StatementFactory
import com.intellij.debugger.streams.core.trace.dsl.TryBlock
import com.intellij.debugger.streams.core.trace.dsl.Variable

/**
 * @author Vitaliy.Bibaev
 */
abstract class TryBlockBase(protected val statementFactory: StatementFactory) : TryBlock {
  protected var myCatchDescriptor: CatchBlockDescriptor? = null

  override val isCatchAdded: Boolean
    get() = myCatchDescriptor != null

  override fun catch(variable: Variable, handler: CodeBlock.() -> Unit) {
    val catchBlock = statementFactory.createEmptyCodeBlock()
    catchBlock.handler()
    myCatchDescriptor = CatchBlockDescriptor(variable, catchBlock)
  }

  protected data class CatchBlockDescriptor(val variable: Variable, val block: CodeBlock)
}