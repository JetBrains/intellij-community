package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.impl.source.tree.ElementType;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 7, 2004
 * Time: 5:17:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlLexer extends BaseHtmlLexer {
  private static IElementType styleElementType;
  private static IElementType inlineStyleElementType;

  private IElementType myTokenType;
  private int myTokenStart;
  private int myTokenEnd;
  private boolean mySkippingTokens;

  public void start(char[] buffer) {
    myTokenType = null;
    super.start(buffer);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myTokenType = null;
    super.start(buffer, startOffset, endOffset);
  }

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
    if (mySkippingTokens) return tokenType;

    myTokenStart = super.getTokenStart();
    myTokenEnd = super.getTokenEnd();

    if (hasSeenStyle() && hasSeenTag() && styleElementType!=null &&
        (tokenType == XmlTokenType.XML_DATA_CHARACTERS ||
         tokenType == XmlTokenType.XML_COMMENT_START ||
         tokenType == ElementType.WHITE_SPACE
        )) {
      assert hasSeenTag();

      mySkippingTokens = true;
      // we need to advance till the end of tag
      int lastState = 0;
      int lastStart = 0;
      while(hasSeenStyle()) {
        lastState = super.getState();
        myTokenEnd = super.getTokenEnd();
        lastStart = super.getTokenStart();
        if (myTokenEnd == getBufferEnd()) break;
        super.advance();
      }

      super.start(getBuffer(),lastStart,getBufferEnd(),lastState);
      super.getTokenType();
      mySkippingTokens = false;
      tokenType = styleElementType;
    } else if (inlineStyleElementType!=null && tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN && hasSeenStyle() && hasSeenAttribute()) {
      tokenType = inlineStyleElementType;
    }
    return myTokenType = tokenType;
  }

  public HtmlLexer() {
    this(new _HtmlLexer(),true);
  }

  protected HtmlLexer(Lexer _baseLexer, boolean _caseInsensitive) {
    super(_baseLexer,_caseInsensitive);
  }

  public static final void setStyleElementTypes(IElementType _styleElementType,IElementType _inlineStyleElementType) {
    styleElementType = _styleElementType;
    inlineStyleElementType = _inlineStyleElementType;
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
}
