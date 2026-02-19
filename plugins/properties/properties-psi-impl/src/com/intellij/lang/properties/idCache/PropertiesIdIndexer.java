// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.idCache;

import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;
import org.jetbrains.annotations.NotNull;

public final class PropertiesIdIndexer extends LexerBasedIdIndexer {
  @Override
  public @NotNull Lexer createLexer(final @NotNull OccurrenceConsumer consumer) {
    return createIndexingLexer(consumer);
  }

  static Lexer createIndexingLexer(OccurrenceConsumer consumer) {
    return new PropertiesFilterLexer(new PropertiesLexer(), consumer);
  }
}
