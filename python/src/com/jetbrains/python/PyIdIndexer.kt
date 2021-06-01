// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer
import com.jetbrains.python.lexer.PythonLexer

class PyIdIndexer : LexerBasedIdIndexer() {
  override fun createLexer(consumer: OccurrenceConsumer): Lexer {
    return createIndexingLexer(consumer)
  }

  companion object {
    fun createIndexingLexer(consumer: OccurrenceConsumer): Lexer {
      return PyFilterLexer(PythonLexer(), consumer)
    }
  }
}