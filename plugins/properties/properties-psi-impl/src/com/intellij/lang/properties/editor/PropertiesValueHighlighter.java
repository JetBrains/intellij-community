// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesHighlighterImpl;
import com.intellij.lexer.Lexer;
import org.jetbrains.annotations.NotNull;

public class PropertiesValueHighlighter extends PropertiesHighlighterImpl {

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new PropertiesValueHighlightingLexer();
  }
}
