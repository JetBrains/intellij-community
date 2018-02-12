// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.CodeBlock
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.impl.common.IfBranchBase

/**
 * @author Vitaliy.Bibaev
 */
class JavaIfBranch(condition: Expression, codeBlock: CodeBlock, statementFactory: JavaStatementFactory)
  : IfBranchBase(condition, codeBlock, statementFactory) {
  override fun toCode(indent: Int): String {
    val elseBlockVar = elseBlock
    val ifThen = "if(${condition.toCode(0)}) {\n".withIndent(indent) +
                 thenBlock.toCode(indent + 1) +
                 "}".withIndent(indent)
    if (elseBlockVar != null) {
      return ifThen + " else { \n" +
             elseBlockVar.toCode(indent + 1) +
             "}".withIndent(indent)
    }

    return ifThen
  }
}