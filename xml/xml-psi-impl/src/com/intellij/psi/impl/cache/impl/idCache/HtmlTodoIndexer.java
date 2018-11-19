// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;

public class HtmlTodoIndexer extends LexerBasedTodoIndexer {
  @Override
  public Lexer createLexer(OccurrenceConsumer consumer) {
    return HtmlIdIndexer.createIndexingLexer(consumer);
  }
}
