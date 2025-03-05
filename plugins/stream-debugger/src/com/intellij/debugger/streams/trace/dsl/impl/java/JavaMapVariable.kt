// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.core.trace.dsl.*
import com.intellij.debugger.streams.core.trace.dsl.impl.common.MapVariableBase
import com.intellij.debugger.streams.core.trace.impl.handler.type.MapType

/**
 * @author Vitaliy.Bibaev
 */
class JavaMapVariable(type: MapType, name: String)
  : MapVariableBase(type, name) {
  override fun get(key: Expression): Expression = call("get", key)

  override operator fun set(key: Expression, newValue: Expression): Expression = call("put", key, newValue)

  override fun contains(key: Expression): Expression = call("containsKey", key)

  override fun keys(): Expression = call("keySet")

  override fun size(): Expression = call("size")

  override fun computeIfAbsent(dsl: Dsl, key: Expression, valueIfAbsent: Expression, target: Variable): CodeBlock {
    return dsl.block {
      target assign call("computeIfAbsent", key, lambda("compIfAbsentKey") {
        doReturn(valueIfAbsent)
      })
    }
  }

  override fun defaultDeclaration(isMutable: Boolean): VariableDeclaration =
    JavaVariableDeclaration(this, false, type.defaultValue)

  override fun entries(): Expression = call("entrySet")
}