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

import com.intellij.lang.HtmlInlineScriptTokenTypesProvider;
import com.intellij.lang.HtmlScriptContentProvider;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageHtmlInlineScriptTokenTypesProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class HtmlHighlightingLexer extends BaseHtmlLexer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.HtmlHighlightingLexer");

  private static final int EMBEDDED_LEXER_ON = 0x1 << BASE_STATE_SHIFT;
  private static final int EMBEDDED_LEXER_STATE_SHIFT = BASE_STATE_SHIFT + 1;
  private static final FileType ourInlineScriptFileType;
  static {
    // At the moment only JS.
    HtmlInlineScriptTokenTypesProvider provider =
      LanguageHtmlInlineScriptTokenTypesProvider.getInlineScriptProvider(Language.findLanguageByID("JavaScript"));
    ourInlineScriptFileType = provider != null ? provider.getFileType() : null;
  }
  private final FileType ourStyleFileType;// = FileTypeManager.getInstance().getStdFileType("CSS");
  protected Lexer elLexer;
  private Lexer embeddedLexer;
  private final Map<String, Lexer> scriptLexers = new HashMap<>();
  private final Map<String, Lexer> styleLexers = new HashMap<>();
  private boolean hasNoEmbeddments;
  private final boolean hasNoLayers;

  public HtmlHighlightingLexer() {
    this(null);
  }

  public HtmlHighlightingLexer(FileType styleFileType) {
    this(new MergingLexerAdapter(new FlexAdapter(new _HtmlLexer()), TOKENS_TO_MERGE), true, styleFileType);
  }

  protected HtmlHighlightingLexer(Lexer lexer, boolean caseInsensitive, FileType styleFileType) {
    super(lexer, caseInsensitive);
    hasNoLayers = Boolean.TRUE.equals(LayeredLexer.ourDisableLayersFlag.get());
    ourStyleFileType = styleFileType;

    XmlEmbeddmentHandler value = new XmlEmbeddmentHandler();
    registerHandler(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, value);
    registerHandler(XmlTokenType.XML_DATA_CHARACTERS, value);
    registerHandler(XmlTokenType.XML_COMMENT_CHARACTERS, value);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    super.start(buffer, startOffset, endOffset, initialState);

    if ((initialState & EMBEDDED_LEXER_ON) != 0) {
      int state = initialState >> EMBEDDED_LEXER_STATE_SHIFT;
      setEmbeddedLexer();
      LOG.assertTrue(embeddedLexer != null);
      embeddedLexer.start(buffer, startOffset, skipToTheEndOfTheEmbeddment(), state);
    }
    else {
      embeddedLexer = null;
      scriptLexers.clear();
      styleLexers.clear();
    }
  }

  private void setEmbeddedLexer() {
    if (hasNoLayers) LayeredLexer.ourDisableLayersFlag.set(Boolean.TRUE);
    try {
      Lexer newLexer = null;
      if (hasSeenStyle()) {
        Lexer styleLexer = styleLexers.get(styleType);
        if (styleLexer == null) {
          if (hasSeenTag()) {
            IElementType currentStylesheetElementType = getCurrentStylesheetElementType();
            if (currentStylesheetElementType != null) {
              Language language = currentStylesheetElementType.getLanguage();
              styleLexer = SyntaxHighlighterFactory.getSyntaxHighlighter(language, null, null).getHighlightingLexer();
            }
            else if (ourStyleFileType != null) {
              SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(ourStyleFileType, null, null);
              LOG.assertTrue(highlighter != null, ourStyleFileType);
              styleLexer = highlighter.getHighlightingLexer();
            }
            else {
              styleLexer = null;
            }
            styleLexers.put(styleType, styleLexer);
          }
          else if (hasSeenAttribute()) {
            if (ourStyleFileType == null) {
              styleLexer = null;
            }
            else {
              SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(ourStyleFileType, null, null);
              LOG.assertTrue(highlighter != null, ourStyleFileType);
              styleLexer = highlighter.getHighlightingLexer();
            }
          }
        }
        newLexer = styleLexer;
      }
      else if (hasSeenScript()) {
        Lexer scriptLexer = scriptLexers.get(scriptType);
        if (scriptLexer == null) {
          if (hasSeenTag()) {
            HtmlScriptContentProvider provider = findScriptContentProvider(scriptType);
            if (provider != null) {
              scriptLexer = provider.getHighlightingLexer();
            }
            else {
              scriptLexer = SyntaxHighlighterFactory.getSyntaxHighlighter(PlainTextLanguage.INSTANCE, null, null).getHighlightingLexer();
            }
          }
          else if (hasSeenAttribute()) {
            SyntaxHighlighter syntaxHighlighter =
              ourInlineScriptFileType != null ? SyntaxHighlighterFactory.getSyntaxHighlighter(ourInlineScriptFileType, null, null) : null;
            scriptLexer = syntaxHighlighter != null ? syntaxHighlighter.getHighlightingLexer() : null;
          }
          scriptLexers.put(scriptType, scriptLexer);
        }
        newLexer = scriptLexer;
      }
      else {
        newLexer = createELLexer(newLexer);
      }

      if (newLexer != null) {
        embeddedLexer = newLexer;
      }
    } finally {
      if (hasNoLayers) LayeredLexer.ourDisableLayersFlag.set(null);
    }
  }

  @Nullable
  protected Lexer createELLexer(Lexer newLexer) {
    return newLexer;
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
    }
  }

  @Override
  public IElementType getTokenType() {
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
        if (hasSeenTag() && (hasSeenStyle() || hasSeenScript())) {
          tokenType = XmlTokenType.XML_WHITE_SPACE;
        }
        else {
          tokenType = getState() != 0 ? XmlTokenType.TAG_WHITE_SPACE : XmlTokenType.XML_REAL_WHITE_SPACE;
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
    if (embeddedLexer != null) state |= embeddedLexer.getState() << EMBEDDED_LEXER_STATE_SHIFT;

    return state;
  }

  @Override
  protected boolean isHtmlTagState(int state) {
    return state == _HtmlLexer.START_TAG_NAME || state == _HtmlLexer.END_TAG_NAME ||
           state == _HtmlLexer.START_TAG_NAME2 || state == _HtmlLexer.END_TAG_NAME2;
  }

  public void setHasNoEmbeddments(boolean hasNoEmbeddments) {
    this.hasNoEmbeddments = hasNoEmbeddments;
  }

  public class XmlEmbeddmentHandler implements TokenHandler {
    @Override
    public void handleElement(Lexer lexer) {
      if (!hasSeenStyle() && !hasSeenScript() || hasNoEmbeddments) return;
      final IElementType tokenType = lexer.getTokenType();

      if (tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN && hasSeenAttribute() ||
          tokenType == XmlTokenType.XML_DATA_CHARACTERS && hasSeenTag() ||
          tokenType == XmlTokenType.XML_COMMENT_CHARACTERS && hasSeenTag()
        ) {
        setEmbeddedLexer();

        if (embeddedLexer != null) {
          embeddedLexer.start(
            getBufferSequence(),
            HtmlHighlightingLexer.super.getTokenStart(),
            skipToTheEndOfTheEmbeddment(),
            embeddedLexer instanceof EmbedmentLexer ? ((EmbedmentLexer)embeddedLexer).getEmbeddedInitialState(tokenType) : 0
          );

          if (embeddedLexer.getTokenType() == null) {
            // no content for embeddment
            embeddedLexer = null;
          }
        }
      }
    }
  }

  public class ElEmbeddmentHandler implements TokenHandler {
    @Override
    public void handleElement(Lexer lexer) {
      setEmbeddedLexer();
      if (embeddedLexer != null) {
        embeddedLexer.start(getBufferSequence(), HtmlHighlightingLexer.super.getTokenStart(), HtmlHighlightingLexer.super.getTokenEnd());
      }
    }
  }
}
