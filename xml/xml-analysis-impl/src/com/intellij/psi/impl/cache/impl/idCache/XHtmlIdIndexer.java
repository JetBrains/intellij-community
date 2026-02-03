// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;
import org.jetbrains.annotations.NotNull;

public class XHtmlIdIndexer extends LexerBasedIdIndexer {
  @Override
  public @NotNull Lexer createLexer(final @NotNull OccurrenceConsumer consumer) {
    return createIndexingLexer(consumer);
  }

  static XHtmlFilterLexer createIndexingLexer(OccurrenceConsumer consumer) {
    return new XHtmlFilterLexer(new XHtmlLexer(true), consumer);
  }

  @Override
  public int getVersion() {
    return 2;
  }
}
