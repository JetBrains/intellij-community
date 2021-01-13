/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lexer;

import com.intellij.html.embedding.HtmlEmbedment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlHighlightingLexer extends BaseHtmlLexer {
  private static final Logger LOG = Logger.getInstance(HtmlHighlightingLexer.class);

  private final int EMBEDDED_LEXER_ON = 0x1 << getBaseStateShift();

  private final FileType ourStyleFileType;// = FileTypeManager.getInstance().getStdFileType("CSS");
  private Lexer embeddedLexer;
  private boolean hasNoEmbedments;
  private final boolean hasNoLayers;

  public HtmlHighlightingLexer() {
    this(null);
  }

  public HtmlHighlightingLexer(@Nullable FileType styleFileType) {
    this(new MergingLexerAdapter(new FlexAdapter(new _HtmlLexer()), TOKENS_TO_MERGE), true, styleFileType);
  }

  protected HtmlHighlightingLexer(@NotNull Lexer lexer, boolean caseInsensitive,
                                  @Nullable FileType styleFileType) {
    super(lexer, caseInsensitive);
    hasNoLayers = Boolean.TRUE.equals(LayeredLexer.ourDisableLayersFlag.get());
    ourStyleFileType = styleFileType;
  }

  public @Nullable FileType getStyleFileType() {
    return ourStyleFileType;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);

    if ((initialState & EMBEDDED_LEXER_ON) != 0) {
      LOG.error(new IllegalStateException("Do not reset HTML Lexer to a state with embedded lexer on"));
    }
    embeddedLexer = null;
  }

  @Override
  public void advance() {
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
        if (hasNoLayers) LayeredLexer.ourDisableLayersFlag.set(null);
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

  @Override
  public @Nullable IElementType getTokenType() {
    if (embeddedLexer != null) {
      return embeddedLexer.getTokenType();
    }
    else {
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
  }

  @Override
  public int getTokenStart() {
    if (embeddedLexer != null) {
      return embeddedLexer.getTokenStart();
    }
    else {
      return super.getTokenStart();
    }
  }

  @Override
  public int getTokenEnd() {
    if (embeddedLexer != null) {
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
}
