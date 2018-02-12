// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.*

/**
 * @author Vitaliy.Bibaev
 */
class JavaForLoop(private val initialization: VariableDeclaration,
                  private val condition: Expression,
                  private val afterThought: Expression,
                  private val loopBody: ForLoopBody) : Convertable {
  override fun toCode(indent: Int): String {
    return "for (${initialization.toCode()}; ${condition.toCode()}; ${afterThought.toCode()}) {\n".withIndent(indent) +
           loopBody.toCode(indent + 1) +
           "}".withIndent(indent)
  }
}