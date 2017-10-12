// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl

import com.intellij.debugger.streams.trace.dsl.*

/**
 * TODO: Add ability to add braces at the beginning and at the end
 *
 * @author Vitaliy.Bibaev
 */
abstract class LineSeparatedCodeBlock(statementFactory: StatementFactory, private val statementSeparator: String = "")
  : CodeBlockBase(statementFactory) {
  override fun toCode(indent: Int): String {
    if (size == 0) {
      return ""
    }

    val builder = StringBuilder()
    val statements = getStatements()
    for (convertable in statements) {
      builder.append(convertable.toCode(indent))
      if (convertable is Statement) {
        builder.append(statementSeparator)
      }
      builder.append("\n")
    }
    return builder.toString()
  }
}