// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexerKt;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet XML_COMMENT_BIT_SET = TokenSet.create(XmlTokenType.XML_COMMENT_CHARACTERS);

  @Override
  public @Nullable Lexer getIndexingLexer(@NotNull PsiFile file) {
    if (file instanceof XmlFile) {
      return XmlLexerKt.createXmlLexer();
    }
    return null;
  }

  @Override
  public @Nullable TokenSet getCommentTokenSet(@NotNull PsiFile file) {
    if (file instanceof XmlFile) {
      return XML_COMMENT_BIT_SET;
    }
    return null;
  }

  @Override
  public int getCommentStartDelta(IElementType tokenType) {
    return tokenType == XmlTokenType.XML_COMMENT_START ? 4 : 0;
  }

  @Override
  public int getCommentEndDelta(IElementType tokenType) {
    return tokenType == XmlTokenType.XML_COMMENT_END ? 3 : 0;
  }
}
