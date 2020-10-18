// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

public class XmlHighlightingLexer extends DelegateLexer {
  public XmlHighlightingLexer() {
    super(new XmlLexer());
  }

  @Override
  public IElementType getTokenType() {
    IElementType tokenType = getDelegate().getTokenType();

    if (tokenType == null) return null;

    tokenType = fixWrongTokenTypes(tokenType);

    return tokenType;
  }

  private IElementType fixWrongTokenTypes(IElementType tokenType) {
    int state = getState() & 0x1f; // __XmlLexer.C_COMMENT_END is last state with value 30
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
