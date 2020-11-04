// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.html.embedding.HtmlEmbedment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Maxim.Mossienko
 */
public class HtmlLexer extends BaseHtmlLexer {

  private IElementType myTokenType;
  private int myTokenStart;
  private int myTokenEnd;


  public HtmlLexer() {
    this(new MergingLexerAdapter(new FlexAdapter(new _HtmlLexer()), TOKENS_TO_MERGE), true);
  }

  protected HtmlLexer(@NotNull Lexer baseLexer, boolean caseInsensitive) {
    super(baseLexer, caseInsensitive);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myTokenType = null;
    super.start(buffer, startOffset, endOffset, initialState);
  }

  @Override
  public void advance() {
    myTokenType = null;
    super.advance();
  }

  @Override
  public @Nullable IElementType getTokenType() {
    if (myTokenType != null) return myTokenType;
    IElementType tokenType = super.getTokenType();

    myTokenStart = super.getTokenStart();
    myTokenEnd = super.getTokenEnd();

    HtmlEmbedment embedment = acquireHtmlEmbedmentInfo();
    if (embedment != null) {
      skipEmbedment(embedment);
      myTokenEnd = embedment.getRange().getEndOffset();
      myTokenType = Objects.requireNonNullElse(embedment.getElementType(), XmlTokenType.XML_DATA_CHARACTERS);
    } else {
      myTokenType = tokenType;
    }
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    if (myTokenType != null) {
      return myTokenStart;
    }
    return super.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    if (myTokenType != null) {
      return myTokenEnd;
    }
    return super.getTokenEnd();
  }
}
