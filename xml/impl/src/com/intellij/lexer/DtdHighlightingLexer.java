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

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author mike
 */
public class DtdHighlightingLexer extends DelegateLexer {
  public DtdHighlightingLexer() {
    super(new OldXmlLexer());
  }

  public IElementType getTokenType() {
    IElementType tokenType = super.getTokenType();

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
}
