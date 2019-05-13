// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl

/**
 * @author Vitaliy.Bibaev
 */
interface CompositeCodeBlock : CodeBlock {
  override fun add(block: CodeBlock) {
    block.getStatements().forEach({ addStatement(it) })
  }

  fun addStatement(statement: Convertable)
}