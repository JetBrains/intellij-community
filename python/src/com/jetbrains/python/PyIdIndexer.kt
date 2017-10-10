// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.index.PrebuiltIndexAwareIdIndexer
import com.intellij.lexer.Lexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer
import com.intellij.util.indexing.FileContent
import com.jetbrains.python.lexer.PythonLexer
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider

/**
 * @author traff
 */
class PyIdIndexer : PrebuiltIndexAwareIdIndexer() {
  private val myIndexer = MyPyIdIndexer()

  override val dirName: String get() = PyPrebuiltStubsProvider.NAME

  override fun idIndexMap(inputData: FileContent): Map<IdIndexEntry, Int> {
    return myIndexer.map(inputData)
  }

  companion object {
    fun createIndexingLexer(consumer: OccurrenceConsumer): Lexer {
      return PyFilterLexer(PythonLexer(), consumer)
    }
  }

  private class MyPyIdIndexer : LexerBasedIdIndexer() {
    override fun createLexer(consumer: OccurrenceConsumer): Lexer {
      return createIndexingLexer(consumer)
    }
  }
}