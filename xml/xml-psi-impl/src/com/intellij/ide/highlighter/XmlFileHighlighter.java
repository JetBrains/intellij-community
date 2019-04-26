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

import com.intellij.lexer.DtdLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlHighlightingLexer;
import com.intellij.lexer.XmlHighlightingLexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.psi.xml.XmlTokenType.*;

public class XmlFileHighlighter extends SyntaxHighlighterBase {
  static final ExtensionPointName<EmbeddedTokenHighlighter> EMBEDDED_HIGHLIGHTERS = ExtensionPointName.create("com.intellij.embeddedTokenHighlighter");
  private static final MultiMap<IElementType, TextAttributesKey> ourMap = MultiMap.create();

  static {
    ourMap.putValue(XML_DATA_CHARACTERS, XmlHighlighterColors.XML_TAG_DATA);

    for (IElementType type : ContainerUtil.ar(XML_COMMENT_START, XML_COMMENT_END, XML_COMMENT_CHARACTERS,
                                              XML_CONDITIONAL_COMMENT_END, XML_CONDITIONAL_COMMENT_END_START,
                                              XML_CONDITIONAL_COMMENT_START, XML_CONDITIONAL_COMMENT_START_END)) {
      ourMap.putValue(type, XmlHighlighterColors.XML_COMMENT);
    }

    for (IElementType type : ContainerUtil.ar(XML_START_TAG_START, XML_END_TAG_START, XML_TAG_END, XML_EMPTY_ELEMENT_END, TAG_WHITE_SPACE)) {
      ourMap.putValue(type, XmlHighlighterColors.XML_TAG);
    }
    for (IElementType type : ContainerUtil.ar(XML_TAG_NAME, XML_CONDITIONAL_IGNORE, XML_CONDITIONAL_INCLUDE)) {
      ourMap.putValues(type, Arrays.asList(XmlHighlighterColors.XML_TAG, XmlHighlighterColors.XML_TAG_NAME));
    }
    ourMap.putValues(XML_NAME, Arrays.asList(XmlHighlighterColors.XML_TAG, XmlHighlighterColors.XML_ATTRIBUTE_NAME));
    for (IElementType type : ContainerUtil.ar(XML_EQ, XML_TAG_CHARACTERS,
                                              XML_ATTRIBUTE_VALUE_TOKEN, XML_ATTRIBUTE_VALUE_START_DELIMITER, XML_ATTRIBUTE_VALUE_END_DELIMITER)) {
      ourMap.putValues(type, Arrays.asList(XmlHighlighterColors.XML_TAG, XmlHighlighterColors.XML_ATTRIBUTE_VALUE));
    }

    for (IElementType type : ContainerUtil.ar(XML_DECL_START, XML_DOCTYPE_START, XML_DOCTYPE_SYSTEM, XML_DOCTYPE_PUBLIC,
                                              XML_ATTLIST_DECL_START, XML_ELEMENT_DECL_START, XML_ENTITY_DECL_START)) {
      ourMap.putValues(type, Arrays.asList(XmlHighlighterColors.XML_TAG, XmlHighlighterColors.XML_TAG_NAME));
    }

    for (IElementType type : ContainerUtil.ar(XML_CONDITIONAL_SECTION_START, XML_CONDITIONAL_SECTION_END, XML_DECL_END, XML_DOCTYPE_END)) {
      ourMap.putValues(type, Arrays.asList(XmlHighlighterColors.XML_PROLOGUE, XmlHighlighterColors.XML_TAG_NAME));
    }

    ourMap.putValue(XML_PI_START, XmlHighlighterColors.XML_PROLOGUE);
    ourMap.putValue(XML_PI_END, XmlHighlighterColors.XML_PROLOGUE);

    ourMap.putValue(XML_CHAR_ENTITY_REF, XmlHighlighterColors.XML_ENTITY_REFERENCE);
    ourMap.putValue(XML_ENTITY_REF_TOKEN, XmlHighlighterColors.XML_ENTITY_REFERENCE);

    ourMap.putValue(XML_BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    for (EmbeddedTokenHighlighter highlighter : EMBEDDED_HIGHLIGHTERS.getExtensionList()) {
      MultiMap<IElementType, TextAttributesKey> attributes = highlighter.getEmbeddedTokenAttributes();
      for (Map.Entry<IElementType, Collection<TextAttributesKey>> entry : attributes.entrySet()) {
        if (!ourMap.containsKey(entry.getKey())) {
          ourMap.putValues(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  private final boolean myIsDtd;
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

  @Override
  @NotNull
  public Lexer getHighlightingLexer() {
    if (myIsDtd) {
      return new DtdLexer(true);
    } else if (myIsXHtml) {
      return new XHtmlHighlightingLexer();
    } else {
      return new XmlHighlightingLexer();
    }
  }

  @Override
  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    //noinspection SynchronizationOnGetClass,SynchronizeOnThis
    synchronized (getClass()) {
      return ourMap.get(tokenType).toArray(EMPTY);
    }
  }

  /**
   * @deprecated use {@link EmbeddedTokenHighlighter} extension
   */
  @Deprecated
  public static synchronized void registerEmbeddedTokenAttributes(Map<IElementType, TextAttributesKey> _keys1,
                                                     Map<IElementType, TextAttributesKey> _keys2) {
    HashSet<IElementType> existingKeys = new HashSet<>(ourMap.keySet());
    addMissing(_keys1, existingKeys, ourMap);
    addMissing(_keys2, existingKeys, ourMap);
  }

  static void addMissing(Map<IElementType, TextAttributesKey> from, Set<IElementType> existingKeys, MultiMap<IElementType, TextAttributesKey> to) {
    if (from != null) {
      for (Map.Entry<IElementType, TextAttributesKey> entry : from.entrySet()) {
        if (!existingKeys.contains(entry.getKey())) {
          to.putValue(entry.getKey(), entry.getValue());
        }
      }
    }
  }
}
