// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.ListType

/**
 * @author Vitaliy.Bibaev
 */
interface ListVariable : Variable {
  override val type: ListType

  fun get(index: Expression): Expression
  fun get(index: Int): Expression = get(TextExpression(index.toString()))

  fun set(index: Expression, newValue: Expression): Expression
  fun set(index: Int, newValue: Expression): Expression = set(TextExpression(index.toString()), newValue)

  fun contains(element: Expression): Expression
  fun size(): Expression

  fun add(element: Expression): Expression

  fun defaultDeclaration(): VariableDeclaration
}