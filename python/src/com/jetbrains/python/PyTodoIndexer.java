// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import org.jetbrains.annotations.NotNull;

public final class PyTodoIndexer extends LexerBasedTodoIndexer {
  @NotNull
  @Override
  public Lexer createLexer(@NotNull OccurrenceConsumer consumer) {
    return PyIdIndexer.Companion.createIndexingLexer(consumer);
  }
}
