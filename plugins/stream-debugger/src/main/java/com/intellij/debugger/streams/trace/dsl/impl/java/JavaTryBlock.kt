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

import com.intellij.debugger.streams.trace.dsl.CodeBlock
import com.intellij.debugger.streams.trace.dsl.StatementFactory
import com.intellij.debugger.streams.trace.dsl.TryBlock
import com.intellij.debugger.streams.trace.dsl.Variable

/**
 * @author Vitaliy.Bibaev
 */
class JavaTryBlock(private val block: CodeBlock, private val statementFactory: StatementFactory) : TryBlock {
  private var myCatchDescriptor: CatchBlockDescriptor? = null

  override val isCatchAdded: Boolean
    get() = myCatchDescriptor != null

  override fun catch(variable: Variable, handler: CodeBlock.() -> Unit) {
    val catchBlock = statementFactory.createEmptyCodeBlock()
    catchBlock.handler()
    myCatchDescriptor = CatchBlockDescriptor(variable, catchBlock)
  }

  override fun toCode(indent: Int): String {
    val descriptor = myCatchDescriptor ?: error("catch block must be specified")
    return "try {\n".withIndent(indent) +
           block.toCode(indent + 1) +
           "} catch(${statementFactory.createVariableDeclaration(descriptor.variable, true).toCode()}) {\n" +
           descriptor.block.toCode(indent + 1) +
           "}".withIndent(indent)
  }

  private data class CatchBlockDescriptor(val variable: Variable, val block: CodeBlock)
}