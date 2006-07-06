package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author mike
 */
public class XmlHighlightingLexer extends LexerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.XmlHighlightingLexer");

  private XmlLexer myLexer = new XmlLexer();

  public XmlHighlightingLexer() {
  }

  public void advance() {
    myLexer.advance();
  }

  public char[] getBuffer() {
    return myLexer.getBuffer();
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

    int state = getState() & 0xF;

    tokenType = fixWrongTokenTypes(tokenType, state);
    if (tokenType != XmlTokenType.XML_COMMENT_CHARACTERS &&
      tokenType != XmlTokenType.XML_COMMENT_END &&
      tokenType != XmlTokenType.XML_COMMENT_START &&
      tokenType != XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {

      // TODO: do not know when this happens!
      switch (state) {
        case __XmlLexer.DOCTYPE:
          tokenType = XmlTokenType.XML_DECL_START;
          break;
      }
    }

    return tokenType;
  }

  static IElementType fixWrongTokenTypes(IElementType tokenType, final int state) {
    if (tokenType == XmlTokenType.XML_NAME) {
      if (state == __XmlLexer.TAG || state == __XmlLexer.END_TAG) {
        // translate XML names for tags into XmlTagName
        tokenType = XmlTokenType.XML_TAG_NAME;
      }
    } else if (tokenType == XmlTokenType.XML_WHITE_SPACE) {
      switch (state) {
        case __XmlLexer.ATTR_LIST:
        case __XmlLexer.ATTR:
          tokenType = XmlTokenType.TAG_WHITE_SPACE; break;
        default:
          tokenType = XmlTokenType.XML_REAL_WHITE_SPACE; break;
      }
    } else if (tokenType == XmlTokenType.XML_CHAR_ENTITY_REF ||
               tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN
              ) {
      if (state == __XmlLexer.COMMENT) return XmlTokenType.XML_COMMENT_CHARACTERS;
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
