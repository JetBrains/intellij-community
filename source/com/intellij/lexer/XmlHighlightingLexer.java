package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author mike
 */
public class XmlHighlightingLexer implements Lexer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.XmlHighlightingLexer");

  private XmlLexer myLexer = new XmlLexer();

  public XmlHighlightingLexer() {
  }

  public void advance() {
    myLexer.advance();
  }

  public Object clone() {
    try {
      XmlHighlightingLexer lexer = (XmlHighlightingLexer)super.clone();
      lexer.myLexer = (XmlLexer)myLexer.clone();
      return lexer;
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return null;
    }
  }

  public char[] getBuffer() {
    return myLexer.getBuffer();
  }

  public int getBufferEnd() {
    return myLexer.getBufferEnd();
  }

  public int getSmartUpdateShift() // number of characters to shift back from the change start to reparse
  {
    return myLexer.getSmartUpdateShift();
  }

  public int getState() {
    return myLexer.getState();
  }

  public int getLastState() {
    return myLexer.getLastState();
  }

  public int getTokenEnd() {
    return myLexer.getTokenEnd();
  }

  public int getTokenStart() {
    return myLexer.getTokenStart();
  }

  public IElementType getTokenType() {
    IElementType tokenType = myLexer.getTokenType();

    if (tokenType == null) return tokenType;

    int state = getState() & 0xF;

    tokenType = fixWrongTokenTypes(tokenType, state);
    if (tokenType != XmlTokenType.XML_COMMENT_CHARACTERS &&
      tokenType != XmlTokenType.XML_COMMENT_END &&
      tokenType != XmlTokenType.XML_COMMENT_START &&
      tokenType != XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {

      // TODO: do not know when this happens!
      switch (state) {
        case _XmlLexer.DOCTYPE:
          tokenType = XmlTokenType.XML_DECL_START;
          break;
      }
    }

    return tokenType;
  }

  static IElementType fixWrongTokenTypes(IElementType tokenType, final int state) {
    if (tokenType == XmlTokenType.XML_NAME) {
      if (state == _XmlLexer.TAG || state == _XmlLexer.END_TAG) {
        // translate XML names for tags into XmlTagName
        tokenType = XmlTokenType.XML_TAG_NAME;
      }
    } else if (tokenType == XmlTokenType.XML_WHITE_SPACE) {
      switch (state) {
        case _XmlLexer.ATTR_LIST:
        case _XmlLexer.ATTR:
          tokenType = XmlTokenType.TAG_WHITE_SPACE; break;
        default:
          tokenType = XmlTokenType.XML_REAL_WHITE_SPACE; break;
      }
    }
    return tokenType;
  }

  public void start(char[] buffer) {
    myLexer.start(buffer);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myLexer.start(buffer, startOffset, endOffset);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myLexer.start(buffer, startOffset, endOffset, initialState);
  }
}
