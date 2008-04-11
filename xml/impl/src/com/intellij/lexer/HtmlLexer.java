package com.intellij.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 7, 2004
 * Time: 5:17:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlLexer extends BaseHtmlLexer {
  private static IElementType ourStyleElementType;
  private static IElementType ourInlineStyleElementType;
  private static IElementType ourScriptElementType;
  private static IElementType ourInlineScriptElementType;

  private IElementType myTokenType;
  private int myTokenStart;
  private int myTokenEnd;

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
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

  private boolean isStartOfEmbeddmentAttributeValue(final IElementType tokenType) {
    return tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
  }

  private boolean isStartOfEmbeddmentTagContent(final IElementType tokenType) {
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

  public static final void setStyleElementTypes(IElementType _styleElementType,IElementType _inlineStyleElementType) {
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
