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
package com.intellij.ide.highlighter;

import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HtmlFileHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> keys1;
  private static final Map<IElementType, TextAttributesKey> keys2;

  static {
    keys1 = new HashMap<>();
    keys2 = new HashMap<>();

    keys1.put(XmlTokenType.XML_COMMENT_START, XmlHighlighterColors.HTML_COMMENT);
    keys1.put(XmlTokenType.XML_COMMENT_END, XmlHighlighterColors.HTML_COMMENT);
    keys1.put(XmlTokenType.XML_COMMENT_CHARACTERS, XmlHighlighterColors.HTML_COMMENT);
    keys1.put(XmlTokenType.XML_CONDITIONAL_COMMENT_END, XmlHighlighterColors.HTML_COMMENT);
    keys1.put(XmlTokenType.XML_CONDITIONAL_COMMENT_END_START, XmlHighlighterColors.HTML_COMMENT);
    keys1.put(XmlTokenType.XML_CONDITIONAL_COMMENT_START, XmlHighlighterColors.HTML_COMMENT);
    keys1.put(XmlTokenType.XML_CONDITIONAL_COMMENT_START_END, XmlHighlighterColors.HTML_COMMENT);

    keys1.put(XmlTokenType.XML_START_TAG_START, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_END_TAG_START, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_TAG_END, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_EMPTY_ELEMENT_END, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_TAG_NAME, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.TAG_WHITE_SPACE, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_NAME, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_TAG_CHARACTERS, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_EQ, XmlHighlighterColors.HTML_TAG);

    keys2.put(XmlTokenType.XML_TAG_NAME, XmlHighlighterColors.HTML_TAG_NAME);
    keys2.put(XmlTokenType.XML_NAME, XmlHighlighterColors.HTML_ATTRIBUTE_NAME);
    keys2.put(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, XmlHighlighterColors.HTML_ATTRIBUTE_VALUE);
    keys2.put(XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlHighlighterColors.HTML_ATTRIBUTE_VALUE);
    keys2.put(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, XmlHighlighterColors.HTML_ATTRIBUTE_VALUE);
    keys2.put(XmlTokenType.XML_EQ, XmlHighlighterColors.HTML_ATTRIBUTE_NAME);

    keys1.put(XmlTokenType.XML_PI_START, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_PI_END, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_PI_TARGET, XmlHighlighterColors.HTML_TAG);
    keys2.put(XmlTokenType.XML_PI_TARGET, XmlHighlighterColors.HTML_TAG_NAME);
    
    keys1.put(XmlTokenType.XML_DOCTYPE_START, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_DOCTYPE_END, XmlHighlighterColors.HTML_TAG);
    keys1.put(XmlTokenType.XML_DOCTYPE_PUBLIC, XmlHighlighterColors.HTML_TAG);

    keys2.put(XmlTokenType.XML_CHAR_ENTITY_REF, XmlHighlighterColors.HTML_ENTITY_REFERENCE);
    keys2.put(XmlTokenType.XML_ENTITY_REF_TOKEN, XmlHighlighterColors.HTML_ENTITY_REFERENCE);

    keys1.put(XmlTokenType.XML_BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);
  }

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    return new HtmlHighlightingLexer(FileTypeRegistry.getInstance().findFileTypeByName("CSS"));
  }

  @Override
  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return SyntaxHighlighterBase.pack(XmlHighlighterColors.HTML_CODE, pack(keys1.get(tokenType), keys2.get(tokenType)));
  }

  public static void registerEmbeddedTokenAttributes(Map<IElementType, TextAttributesKey> _keys1,
                                                           Map<IElementType, TextAttributesKey> _keys2) {
    if (_keys1 != null) {
      for (IElementType iElementType : _keys1.keySet()) {
        if (!keys1.containsKey(iElementType)) {
          keys1.put(iElementType, _keys1.get(iElementType));
        }
      }
    }

    if (_keys2 != null) {
      for (IElementType iElementType : _keys2.keySet()) {
        if (!keys2.containsKey(iElementType)) {
          keys2.put(iElementType, _keys2.get(iElementType));
        }
      }
    }
  }
}
