// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexerKt;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;
import org.jetbrains.annotations.NotNull;

public class XmlIdIndexer extends LexerBasedIdIndexer {
  @Override
  public @NotNull Lexer createLexer(final @NotNull OccurrenceConsumer consumer) {
    return createIndexingLexer(consumer);
  }

  static XmlFilterLexer createIndexingLexer(OccurrenceConsumer consumer) {
    return new XmlFilterLexer(XmlLexerKt.createXmlLexer(), consumer);
  }
}
