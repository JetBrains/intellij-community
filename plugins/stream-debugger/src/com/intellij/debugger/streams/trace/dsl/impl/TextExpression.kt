// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl

import com.intellij.debugger.streams.trace.dsl.Expression

/**
 * @author Vitaliy.Bibaev
 */
class TextExpression(private val myText: String) : Expression {
  override fun call(callName: String, vararg args: Expression): Expression =
    TextExpression("$myText.$callName${args.joinToString(", ", "(", ")")}")

  override fun toString(): String = toCode(0)

  override fun toCode(indent: Int): String = myText.withIndent(indent)
}