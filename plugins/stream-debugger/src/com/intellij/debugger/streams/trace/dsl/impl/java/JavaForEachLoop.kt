// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.Convertable
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.ForLoopBody
import com.intellij.debugger.streams.trace.dsl.Variable

/**
 * @author Vitaliy.Bibaev
 */
class JavaForEachLoop(private val iterateVariable: Variable,
                      private val collection: Expression,
                      private val loopBody: ForLoopBody) : Convertable {
  override fun toCode(indent: Int): String =
    "for (${iterateVariable.type.variableTypeName} ${iterateVariable.name} : ${collection.toCode(0)}) {\n".withIndent(indent) +
    loopBody.toCode(indent + 1) +
    "}".withIndent(indent)
}