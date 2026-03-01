// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer
import com.jetbrains.python.lexer.PythonLexer

internal class PyIdIndexer : LexerBasedIdIndexer() {
  override fun createLexer(consumer: OccurrenceConsumer): Lexer {
    return createPyIndexingLexer(consumer)
  }
}

fun createPyIndexingLexer(consumer: OccurrenceConsumer): Lexer {
  return PyFilterLexer(PythonLexer(), consumer)
}