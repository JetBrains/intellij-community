/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.highlighter;

import com.intellij.lexer.*;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import org.jetbrains.annotations.NotNull;

public class XmlFileHighlighter extends SyntaxHighlighterBase {
  private static Map<IElementType, TextAttributesKey> keys1;
  private static Map<IElementType, TextAttributesKey> keys2;

  static {
    keys1 = new HashMap<IElementType, TextAttributesKey>();
    keys2 = new HashMap<IElementType, TextAttributesKey>();

    keys1.put(XmlTokenType.XML_DATA_CHARACTERS, HighlighterColors.XML_TAG_DATA);

    keys1.put(XmlTokenType.XML_COMMENT_START, HighlighterColors.XML_COMMENT);
    keys1.put(XmlTokenType.XML_COMMENT_END, HighlighterColors.XML_COMMENT);
    keys1.put(XmlTokenType.XML_COMMENT_CHARACTERS, HighlighterColors.XML_COMMENT);

    keys1.put(XmlTokenType.XML_START_TAG_START, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_END_TAG_START, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_TAG_END, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_EMPTY_ELEMENT_END, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_TAG_NAME, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.TAG_WHITE_SPACE, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_NAME, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_CONDITIONAL_IGNORE, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_CONDITIONAL_INCLUDE, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, HighlighterColors.XML_TAG);
    keys1.put(XmlTokenType.XML_EQ, HighlighterColors.XML_TAG);

    keys2.put(XmlTokenType.XML_TAG_NAME, HighlighterColors.XML_TAG_NAME);
    keys2.put(XmlTokenType.XML_CONDITIONAL_INCLUDE, HighlighterColors.XML_TAG_NAME);
    keys2.put(XmlTokenType.XML_CONDITIONAL_INCLUDE, HighlighterColors.XML_TAG_NAME);
    keys2.put(XmlTokenType.XML_NAME, HighlighterColors.XML_ATTRIBUTE_NAME);
    keys2.put(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, HighlighterColors.XML_ATTRIBUTE_VALUE);
    keys2.put(XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, HighlighterColors.XML_ATTRIBUTE_VALUE);
    keys2.put(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, HighlighterColors.XML_ATTRIBUTE_VALUE);
    keys2.put(XmlTokenType.XML_EQ, HighlighterColors.XML_ATTRIBUTE_NAME);

    keys1.put(XmlTokenType.XML_BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    keys1.put(XmlTokenType.XML_DECL_START, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_DECL_START, HighlighterColors.XML_TAG_NAME);

    keys1.put(XmlTokenType.XML_CONDITIONAL_SECTION_START, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_CONDITIONAL_SECTION_START, HighlighterColors.XML_TAG_NAME);

    keys1.put(XmlTokenType.XML_CONDITIONAL_SECTION_END, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_CONDITIONAL_SECTION_END, HighlighterColors.XML_TAG_NAME);

    keys1.put(XmlTokenType.XML_DECL_END, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_DECL_END, HighlighterColors.XML_TAG_NAME);

    keys1.put(XmlTokenType.XML_DOCTYPE_END, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_DOCTYPE_END, HighlighterColors.XML_TAG_NAME);

    keys1.put(XmlTokenType.XML_DOCTYPE_START, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_DOCTYPE_START, HighlighterColors.XML_TAG_NAME);

    keys1.put(XmlTokenType.XML_ATTLIST_DECL_START, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_ATTLIST_DECL_START, HighlighterColors.XML_TAG_NAME);

    keys1.put(XmlTokenType.XML_ELEMENT_DECL_START, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_ELEMENT_DECL_START, HighlighterColors.XML_TAG_NAME);

    keys1.put(XmlTokenType.XML_ENTITY_DECL_START, HighlighterColors.XML_PROLOGUE);
    keys2.put(XmlTokenType.XML_ENTITY_DECL_START, HighlighterColors.XML_TAG_NAME);

    keys2.put(XmlTokenType.XML_CHAR_ENTITY_REF, HighlighterColors.XML_ENTITY_REFERENCE);
    keys2.put(XmlTokenType.XML_ENTITY_REF_TOKEN, HighlighterColors.XML_ENTITY_REFERENCE);
  }

  private boolean myIsDtd;
  private boolean myIsXHtml;

  public XmlFileHighlighter() {
    this(false);
  }

  public XmlFileHighlighter(boolean dtd) {
    myIsDtd = dtd;
  }

  public XmlFileHighlighter(boolean dtd, boolean xhtml) {
    myIsDtd = dtd;
    myIsXHtml = xhtml;
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    if (myIsDtd) {
      return new DtdHighlightingLexer();
    } else if (myIsXHtml) {
      return new XHtmlHighlightingLexer();
    } else {
      return new XmlHighlightingLexer();
    }
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(keys1.get(tokenType), keys2.get(tokenType));
  }

  public static final void registerEmbeddedTokenAttributes(Map<IElementType, TextAttributesKey> _keys1,
                                                           Map<IElementType, TextAttributesKey> _keys2) {
    if (_keys1!=null) {
      for (Iterator<IElementType> iterator = _keys1.keySet().iterator(); iterator.hasNext();) {
        IElementType iElementType = iterator.next();
        keys1.put(iElementType,_keys1.get(iElementType));
      }
    }

    if (_keys2!=null) {
      for (Iterator<IElementType> iterator = _keys2.keySet().iterator(); iterator.hasNext();) {
        IElementType iElementType = iterator.next();
        keys2.put(iElementType,_keys2.get(iElementType));
      }
    }
  }
}