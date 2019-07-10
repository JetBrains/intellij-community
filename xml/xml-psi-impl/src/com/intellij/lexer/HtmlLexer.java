// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.lang.HtmlInlineScriptTokenTypesProvider;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageHtmlInlineScriptTokenTypesProvider;
import com.intellij.lang.LanguageUtil;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class HtmlLexer extends BaseHtmlLexer {
  private static final IElementType ourInlineStyleElementType;
  private static final IElementType ourInlineScriptElementType;

  public static final String INLINE_STYLE_NAME = "css-ruleset-block";

  static {
    List<EmbeddedTokenTypesProvider> extensions = EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME.getExtensionList();
    IElementType inlineStyleElementType = null;
    for (EmbeddedTokenTypesProvider extension : extensions) {
      if (INLINE_STYLE_NAME.equals(extension.getName())) {
        inlineStyleElementType = extension.getElementType();
        break;
      }
    }
    ourInlineStyleElementType = inlineStyleElementType;
    // At the moment only JS.
    HtmlInlineScriptTokenTypesProvider provider = LanguageHtmlInlineScriptTokenTypesProvider.getInlineScriptProvider(ourDefaultLanguage);
    ourInlineScriptElementType = provider != null ? provider.getElementType() : null;
  }

  private IElementType myTokenType;
  private int myTokenStart;
  private int myTokenEnd;

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
  public IElementType getTokenType() {
    if (myTokenType!=null) return myTokenType;
    IElementType tokenType = super.getTokenType();

    myTokenStart = super.getTokenStart();
    myTokenEnd = super.getTokenEnd();

    if (hasSeenStyle()) {
      if (hasSeenTag() && isStartOfEmbeddmentTagContent(tokenType)) {
        Language stylesheetLanguage = getStyleLanguage();
        if (stylesheetLanguage == null || LanguageUtil.isInjectableLanguage(stylesheetLanguage)) {
          myTokenEnd = skipToTheEndOfTheEmbeddment();
          IElementType currentStylesheetElementType = getCurrentStylesheetElementType();
          tokenType = currentStylesheetElementType == null ? XmlTokenType.XML_DATA_CHARACTERS : currentStylesheetElementType;
        }
      } else if (ourInlineStyleElementType!=null && isStartOfEmbeddmentAttributeValue(tokenType) && hasSeenAttribute()) {
        tokenType = ourInlineStyleElementType;
      }
    } else if (hasSeenScript()) {
      if (hasSeenTag() && isStartOfEmbeddmentTagContent(tokenType)) {
        Language scriptLanguage = getScriptLanguage();
        if (scriptLanguage == null || LanguageUtil.isInjectableLanguage(scriptLanguage)) {
          myTokenEnd = skipToTheEndOfTheEmbeddment();
          IElementType currentScriptElementType = getCurrentScriptElementType();
          tokenType = currentScriptElementType == null ? XmlTokenType.XML_DATA_CHARACTERS : currentScriptElementType;
        }
      } else if (hasSeenAttribute() && isStartOfEmbeddmentAttributeValue(tokenType) && ourInlineScriptElementType!=null) {
        myTokenEnd = skipToTheEndOfTheEmbeddment();
        tokenType = ourInlineScriptElementType;
      }
    }

    return myTokenType = tokenType;
  }

  private static boolean isStartOfEmbeddmentAttributeValue(final IElementType tokenType) {
    return tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
  }

  private static boolean isStartOfEmbeddmentTagContent(final IElementType tokenType) {
    return (tokenType == XmlTokenType.XML_DATA_CHARACTERS ||
            tokenType == XmlTokenType.XML_CDATA_START ||
            tokenType == XmlTokenType.XML_COMMENT_START ||
            tokenType == XmlTokenType.XML_START_TAG_START ||
            tokenType == XmlTokenType.XML_REAL_WHITE_SPACE || tokenType == TokenType.WHITE_SPACE ||
            tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN || tokenType == XmlTokenType.XML_CHAR_ENTITY_REF
    );
  }

  public HtmlLexer() {
    this(new MergingLexerAdapter(new FlexAdapter(new _HtmlLexer()), TOKENS_TO_MERGE),true);
  }

  protected HtmlLexer(Lexer _baseLexer, boolean _caseInsensitive) {
    super(_baseLexer,_caseInsensitive);
  }

  @Override
  protected boolean isHtmlTagState(int state) {
    return state == _HtmlLexer.START_TAG_NAME || state == _HtmlLexer.END_TAG_NAME;
  }

  @Override
  public int getTokenStart() {
    if (myTokenType!=null) {
      return myTokenStart;
    }
    return super.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    if (myTokenType!=null) {
      return myTokenEnd;
    }
    return super.getTokenEnd();
  }
}
