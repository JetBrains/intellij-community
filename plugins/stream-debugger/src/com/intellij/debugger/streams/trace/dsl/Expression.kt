// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.dsl.impl.TextExpression


/**
 * @author Vitaliy.Bibaev
 */
interface Expression : Statement {
  fun call(callName: String, vararg args: Expression): Expression

  fun property(propertyName: String): Expression = TextExpression("${toCode()}.$propertyName")
}
