// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.ArrayVariable
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.dsl.impl.VariableImpl
import com.intellij.debugger.streams.trace.impl.handler.type.ArrayType

/**
 * @author Vitaliy.Bibaev
 */
class JavaArrayVariable(override val type: ArrayType, name: String)
  : VariableImpl(type, name), ArrayVariable {
  override fun get(index: Expression): Expression = TextExpression("$name[${index.toCode()}]")

  override fun set(index: Expression, value: Expression): Expression = TextExpression("$name[${index.toCode()}] = ${value.toCode()}")

  override fun defaultDeclaration(size: Expression): VariableDeclaration =
    JavaVariableDeclaration(this, false, type.sizedDeclaration(size.toCode()))
}