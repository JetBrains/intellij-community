// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesHighlighterImpl;
import com.intellij.lexer.Lexer;
import org.jetbrains.annotations.NotNull;

public class PropertiesValueHighlighter extends PropertiesHighlighterImpl {

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new PropertiesValueHighlightingLexer();
  }
}
