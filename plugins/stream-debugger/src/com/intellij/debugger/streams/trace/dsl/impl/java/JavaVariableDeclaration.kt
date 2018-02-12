// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.Variable
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration

/**
 * @author Vitaliy.Bibaev
 */
class JavaVariableDeclaration(override val variable: Variable,
                              override val isMutable: Boolean,
                              private val init: String = "") : VariableDeclaration {
  override fun toCode(indent: Int): String {
    val modifier = if (!isMutable) "final " else ""
    val initExpression = if (init.trim().isEmpty()) "" else " = $init"
    return "$modifier${variable.type.variableTypeName} ${variable.name}$initExpression".withIndent(indent)
  }
}