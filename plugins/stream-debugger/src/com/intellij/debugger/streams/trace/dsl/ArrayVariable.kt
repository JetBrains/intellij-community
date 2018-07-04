// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.ArrayType


/**
 * @author Vitaliy.Bibaev
 */
interface ArrayVariable : Variable {
  override val type: ArrayType

  operator fun get(index: Expression): Expression
  operator fun get(index: Int): Expression = get(TextExpression(index.toString()))
  fun set(index: Expression, value: Expression): Expression
  fun set(index: Int, value: Expression): Expression = set(TextExpression(index.toString()), value)

  fun defaultDeclaration(size: Expression): VariableDeclaration
}