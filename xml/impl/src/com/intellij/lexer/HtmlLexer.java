/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author Maxim.Mossienko
 */
public class HtmlLexer extends BaseHtmlLexer {
  private static IElementType ourStyleElementType;
  private static IElementType ourInlineStyleElementType;
  private static IElementType ourScriptElementType;
  private static IElementType ourInlineScriptElementType;

  private IElementType myTokenType;
  private int myTokenStart;
  private int myTokenEnd;

  @Override
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myTokenType = null;
    super.start(buffer, startOffset, endOffset, initialState);
  }

  public void advance() {
    myTokenType = null;
    super.advance();
  }

  public IElementType getTokenType() {
    if (myTokenType!=null) return myTokenType;
    IElementType tokenType = super.getTokenType();

    myTokenStart = super.getTokenStart();
    myTokenEnd = super.getTokenEnd();

    if (hasSeenStyle()) {
      if (hasSeenTag() && ourStyleElementType!=null && isStartOfEmbeddmentTagContent(tokenType)) {
        myTokenEnd = skipToTheEndOfTheEmbeddment();
        tokenType = ourStyleElementType;
      } else if (ourInlineStyleElementType!=null && isStartOfEmbeddmentAttributeValue(tokenType) && hasSeenAttribute()) {
        tokenType = ourInlineStyleElementType;
      }
    } else if (hasSeenScript()) {
      if (hasSeenTag() && ourScriptElementType!=null && isStartOfEmbeddmentTagContent(tokenType)) {
        myTokenEnd = skipToTheEndOfTheEmbeddment();
        tokenType = ourScriptElementType;
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
            tokenType == XmlTokenType.XML_REAL_WHITE_SPACE || tokenType == TokenType.WHITE_SPACE
    );
  }

  public HtmlLexer() {
    this(new MergingLexerAdapter(new FlexAdapter(new _HtmlLexer()), TOKENS_TO_MERGE),true);
  }

  protected HtmlLexer(Lexer _baseLexer, boolean _caseInsensitive) {
    super(_baseLexer,_caseInsensitive);
  }

  public static void setStyleElementTypes(IElementType _styleElementType,IElementType _inlineStyleElementType) {
    ourStyleElementType = _styleElementType;
    ourInlineStyleElementType = _inlineStyleElementType;
  }

  protected boolean isHtmlTagState(int state) {
    return state == _HtmlLexer.START_TAG_NAME || state == _HtmlLexer.END_TAG_NAME;
  }

  public int getTokenStart() {
    if (myTokenType!=null) {
      return myTokenStart;
    }
    return super.getTokenStart();
  }

  public int getTokenEnd() {
    if (myTokenType!=null) {
      return myTokenEnd;
    }
    return super.getTokenEnd();
  }

  public static void setScriptElementTypes(final IElementType scriptElementType, final IElementType inlineScriptElementType) {
    ourScriptElementType = scriptElementType;
    ourInlineScriptElementType = inlineScriptElementType;
  }
}
