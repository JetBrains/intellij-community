package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author mike
 */
public class XmlHighlightingLexer extends DelegateLexer {
  public XmlHighlightingLexer() {
    super(new XmlLexer());
  }

  public IElementType getTokenType() {
    IElementType tokenType = getDelegate().getTokenType();

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
}
