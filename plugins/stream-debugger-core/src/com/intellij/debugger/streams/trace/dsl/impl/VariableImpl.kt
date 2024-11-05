// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl

import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.Variable
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType

/**
 * @author Vitaliy.Bibaev
 */
open class VariableImpl(override val type: GenericType, override val name: String) : Variable {
  override fun call(callName: String, vararg args: Expression): Expression =
    TextExpression("$name.$callName(${args.joinToString(", ") { it.toCode() }})")

  override fun toString(): String = toCode()
}
