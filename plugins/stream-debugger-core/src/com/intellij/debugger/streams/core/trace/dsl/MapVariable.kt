// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.trace.dsl

import com.intellij.debugger.streams.core.trace.impl.handler.type.MapType

/**
 * @author Vitaliy.Bibaev
 */
interface MapVariable : Variable {
  override val type: MapType

  fun get(key: Expression): Expression
  fun set(key: Expression, newValue: Expression): Expression
  fun contains(key: Expression): Expression
  fun size(): Expression
  fun keys(): Expression

  fun computeIfAbsent(dsl: Dsl, key: Expression, valueIfAbsent: Expression, target: Variable): CodeBlock

  fun defaultDeclaration(isMutable: Boolean = true): VariableDeclaration

  fun entries(): Expression

  fun convertToArray(dsl: Dsl, arrayName: String): CodeBlock
}