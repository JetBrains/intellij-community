// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;
import org.jetbrains.annotations.NotNull;

public class XmlIdIndexer extends LexerBasedIdIndexer {
  @NotNull
  @Override
  public Lexer createLexer(@NotNull final OccurrenceConsumer consumer) {
    return createIndexingLexer(consumer);
  }

  static XmlFilterLexer createIndexingLexer(OccurrenceConsumer consumer) {
    return new XmlFilterLexer(new XmlLexer(), consumer);
  }
}
