package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author mike
 */
public class DtdHighlightingLexer implements Lexer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.XmlHighlightingLexer");

  private Lexer myLexer;

  public DtdHighlightingLexer() {
    myLexer = new OldXmlLexer();
  }

  public void advance() {
    myLexer.advance();
  }

  public Object clone() {
    try {
      DtdHighlightingLexer lexer = (DtdHighlightingLexer)super.clone();
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

    if (tokenType != XmlTokenType.XML_COMMENT_CHARACTERS &&
      tokenType != XmlTokenType.XML_COMMENT_END &&
      tokenType != XmlTokenType.XML_COMMENT_START &&
      tokenType != XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {

      // TODO: do not know when this happens!
      switch (getState()) {
        case _XmlLexer.DOCTYPE:
          tokenType = XmlTokenType.XML_DECL_START;
          break;
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
