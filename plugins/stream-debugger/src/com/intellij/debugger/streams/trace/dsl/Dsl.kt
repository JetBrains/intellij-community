// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

/**
 * @author Vitaliy.Bibaev
 */
interface Dsl : DslFactory {
  val nullExpression: Expression
  val thisExpression: Expression
  val types: Types

  fun block(init: CodeContext.() -> Unit): CodeBlock

  fun code(init: CodeContext.() -> Unit): String
}
