// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.html.embedding.HtmlEmbeddedContentSupport;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @see HtmlEmbeddedContentSupport
 */
public interface HtmlScriptContentProvider {
  /**
   * @return instance of the {@link IElementType} to use in html script tag
   */
  @Nullable
  IElementType getScriptElementType();

  /**
   * @return highlighting lexer to use in html script tag
   */
  @Nullable
  Lexer getHighlightingLexer();

  class Empty implements HtmlScriptContentProvider {
    @Override
    public @Nullable IElementType getScriptElementType() {
      return null;
    }

    @Override
    public @Nullable Lexer getHighlightingLexer() {
      return null;
    }
  }
}
