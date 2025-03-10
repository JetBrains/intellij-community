// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PropertiesHighlighterImpl extends PropertiesHighlighter {
  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new PropertiesHighlightingLexer();
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    final PropertiesComponent type = PropertiesComponent.getByTokenType(tokenType);

    TextAttributesKey key = null;
    if (type != null) {
      key = type.getTextAttributesKey();
    }

    return pack(key);
  }
}