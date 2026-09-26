// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.trace.dsl.impl

import com.intellij.debugger.streams.core.trace.dsl.Expression
import com.intellij.debugger.streams.core.trace.dsl.Statement
import com.intellij.debugger.streams.core.trace.dsl.Variable

/**
 * @author Vitaliy.Bibaev
 */
interface AssignmentStatement : Statement {
  val variable: Variable
  val expression: Expression
}