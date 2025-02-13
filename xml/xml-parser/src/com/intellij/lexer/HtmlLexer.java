// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.html.embedding.HtmlEmbedment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Maxim.Mossienko
 */
public class HtmlLexer extends BaseHtmlLexer {

  private static final Logger LOG = Logger.getInstance(HtmlLexer.class);

  private final boolean highlightMode;

  // Regular mode fields
  private IElementType myTokenType;
  private int myTokenStart;
  private int myTokenEnd;

  // Highlighting mode fields
  private final int EMBEDDED_LEXER_ON = 0x1 << getBaseStateShift();
  private Lexer embeddedLexer;
  private boolean hasNoEmbedments;
  private final boolean hasNoLayers;

  public HtmlLexer() {
    this(new MergingLexerAdapter(new FlexAdapter(new _HtmlLexer()), TOKENS_TO_MERGE), true, false);
  }

  public HtmlLexer(boolean highlightMode) {
    this(new MergingLexerAdapter(new FlexAdapter(new _HtmlLexer()), TOKENS_TO_MERGE), true, highlightMode);
  }

  protected HtmlLexer(@NotNull Lexer baseLexer, boolean caseInsensitive) {
    this(baseLexer, caseInsensitive, false);
  }

  protected HtmlLexer(@NotNull Lexer baseLexer, boolean caseInsensitive, boolean highlightMode) {
    super(baseLexer, caseInsensitive);
    hasNoLayers = Boolean.TRUE.equals(LayeredLexer.ourDisableLayersFlag.get());
    this.highlightMode = highlightMode;
  }

  public boolean isHighlighting() {
    return highlightMode;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myTokenType = null;
    embeddedLexer = null;
    if (highlightMode && (initialState & EMBEDDED_LEXER_ON) != 0) {
      LOG.error(new IllegalStateException("Do not reset HTML Lexer to a state with embedded lexer on"));
    }
    super.start(buffer, startOffset, endOffset, initialState);
  }

  @Override
  public void advance() {
    if (highlightMode) {
      if (embeddedLexer != null) {
        embeddedLexer.advance();
        if (embeddedLexer.getTokenType() == null) {
          embeddedLexer = null;
        }
      }

      if (embeddedLexer == null) {
        super.advance();
        if (!hasNoEmbedments) {
          tryCreateEmbeddedLexer();
        }
      }
    }
    else {
      myTokenType = null;
      super.advance();
    }
  }

  @Override
  public @Nullable IElementType getTokenType() {
    if (embeddedLexer != null) {
      return embeddedLexer.getTokenType();
    }
    else if (myTokenType != null) {
      return myTokenType;
    }
    else if (highlightMode) {
      IElementType tokenType = super.getTokenType();

      // TODO: fix no DOCTYPE highlighting
      if (tokenType == null) return null;

      if (tokenType == XmlTokenType.XML_NAME) {
        // we need to convert single xml_name for tag name and attribute name into to separate
        // lex types for the highlighting!
        final int state = getState() & BASE_STATE_MASK;

        if (isHtmlTagState(state)) {
          tokenType = XmlTokenType.XML_TAG_NAME;
        }
      }
      else if (tokenType == XmlTokenType.XML_WHITE_SPACE || tokenType == XmlTokenType.XML_REAL_WHITE_SPACE) {
        if (embeddedLexer != null && isWithinTag()) {
          tokenType = XmlTokenType.XML_WHITE_SPACE;
        }
        else {
          tokenType = isWithinTag() ? XmlTokenType.TAG_WHITE_SPACE : XmlTokenType.XML_REAL_WHITE_SPACE;
        }
      }
      else if (tokenType == XmlTokenType.XML_CHAR_ENTITY_REF ||
               tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN
      ) {
        // we need to convert char entity ref & entity ref in comments as comment chars
        final int state = getState() & BASE_STATE_MASK;
        if (state == _HtmlLexer.COMMENT) return XmlTokenType.XML_COMMENT_CHARACTERS;
      }
      return tokenType;
    }
    else {
      IElementType tokenType = super.getTokenType();

      myTokenStart = super.getTokenStart();
      myTokenEnd = super.getTokenEnd();

      HtmlEmbedment embedment = acquireHtmlEmbedmentInfo();
      if (embedment != null) {
        skipEmbedment(embedment);
        myTokenEnd = embedment.getRange().getEndOffset();
        myTokenType = Objects.requireNonNullElse(embedment.getElementType(), XmlTokenType.XML_DATA_CHARACTERS);
      }
      else {
        myTokenType = tokenType;
      }
      return myTokenType;
    }
  }

  @Override
  public int getTokenStart() {
    if (myTokenType != null) {
      return myTokenStart;
    }
    else if (embeddedLexer != null) {
      return embeddedLexer.getTokenStart();
    }
    else {
      return super.getTokenStart();
    }
  }

  @Override
  public int getTokenEnd() {
    if (myTokenType != null) {
      return myTokenEnd;
    }
    else if (embeddedLexer != null) {
      return embeddedLexer.getTokenEnd();
    }
    else {
      return super.getTokenEnd();
    }
  }

  @Override
  public int getState() {
    int state = super.getState();
    state |= embeddedLexer != null ? EMBEDDED_LEXER_ON : 0;
    return state;
  }

  protected @Nullable Lexer getEmbeddedLexer() {
    return embeddedLexer;
  }

  public void setHasNoEmbeddments(boolean hasNoEmbedments) {
    this.hasNoEmbedments = hasNoEmbedments;
  }

  private void tryCreateEmbeddedLexer() {
    IElementType token = myDelegate.getTokenType();
    if (token == null) {
      return;
    }
    HtmlEmbedment embedment = acquireHtmlEmbedmentInfo();
    if (embedment != null && !embedment.getRange().isEmpty()) {
      if (hasNoLayers) LayeredLexer.ourDisableLayersFlag.set(Boolean.TRUE);
      try {
        embeddedLexer = embedment.createHighlightingLexer();
      }
      finally {
        if (hasNoLayers) LayeredLexer.ourDisableLayersFlag.remove();
      }
      if (embeddedLexer != null) {
        skipEmbedment(embedment);
        embeddedLexer.start(
          getBufferSequence(),
          embedment.getRange().getStartOffset(),
          embedment.getRange().getEndOffset(),
          embeddedLexer instanceof EmbedmentLexer ? ((EmbedmentLexer)embeddedLexer).getEmbeddedInitialState(myDelegate.getTokenType()) : 0
        );

        if (embeddedLexer.getTokenType() == null) {
          // no content for embedment
          embeddedLexer = null;
        }
      }
    }
  }
}
