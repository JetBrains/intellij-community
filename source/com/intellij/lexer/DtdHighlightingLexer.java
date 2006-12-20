package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author mike
 */
public class DtdHighlightingLexer extends LexerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.XmlHighlightingLexer");

  private Lexer myLexer;

  public DtdHighlightingLexer() {
    myLexer = new OldXmlLexer();
  }

  public void advance() {
    myLexer.advance();
  }

  public char[] getBuffer() {
    return myLexer.getBuffer();
  }

  public CharSequence getBufferSequence() {
    return myLexer.getBufferSequence();
  }

  public int getBufferEnd() {
    return myLexer.getBufferEnd();
  }

  public int getState() {
    return myLexer.getState();
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
        case __XmlLexer.DOCTYPE:
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

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myLexer.start(buffer, startOffset, endOffset, initialState);
  }
}
