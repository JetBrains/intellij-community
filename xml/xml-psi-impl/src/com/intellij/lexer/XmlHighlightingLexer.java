/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author mike
 */
public class XmlHighlightingLexer extends DelegateLexer {
  public XmlHighlightingLexer() {
    super(new XmlLexer());
  }

  @Override
  public IElementType getTokenType() {
    IElementType tokenType = getDelegate().getTokenType();

    if (tokenType == null) return tokenType;

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
