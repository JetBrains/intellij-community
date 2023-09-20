// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.intellij.ide.highlighter.XmlFileHighlighter.EMBEDDED_HIGHLIGHTERS;
import static com.intellij.ide.highlighter.XmlFileHighlighter.registerAdditionalHighlighters;
import static com.intellij.psi.xml.XmlTokenType.*;

public class HtmlFileHighlighter extends SyntaxHighlighterBase {
  private static final MultiMap<IElementType, TextAttributesKey> ourMap = MultiMap.create();

  static {
    ourMap.putValue(XML_TAG_CHARACTERS, XmlHighlighterColors.HTML_TAG);

    for (IElementType type : ContainerUtil.ar(XML_COMMENT_START, XML_COMMENT_END, XML_COMMENT_CHARACTERS,
                                              XML_CONDITIONAL_COMMENT_END, XML_CONDITIONAL_COMMENT_END_START,
                                              XML_CONDITIONAL_COMMENT_START, XML_CONDITIONAL_COMMENT_START_END)) {
      ourMap.putValue(type, XmlHighlighterColors.HTML_COMMENT);
    }

    for (IElementType type : ContainerUtil.ar(XML_START_TAG_START, XML_END_TAG_START, XML_TAG_END, XML_EMPTY_ELEMENT_END, TAG_WHITE_SPACE)) {
      ourMap.putValue(type, XmlHighlighterColors.HTML_TAG);
    }

    ourMap.putValues(XML_TAG_NAME, Arrays.asList(XmlHighlighterColors.HTML_TAG, XmlHighlighterColors.HTML_TAG_NAME));
    ourMap.putValues(XML_NAME, Arrays.asList(XmlHighlighterColors.HTML_TAG, XmlHighlighterColors.HTML_ATTRIBUTE_NAME));
    for (IElementType type : ContainerUtil.ar(XML_EQ,
                                              XML_ATTRIBUTE_VALUE_TOKEN, XML_ATTRIBUTE_VALUE_START_DELIMITER, XML_ATTRIBUTE_VALUE_END_DELIMITER)) {
      ourMap.putValues(type, Arrays.asList(XmlHighlighterColors.HTML_TAG, XmlHighlighterColors.HTML_ATTRIBUTE_VALUE));
    }

    for (IElementType type : ContainerUtil.ar(XML_PI_START, XML_PI_END, XML_DOCTYPE_START, XML_DOCTYPE_END, XML_DOCTYPE_PUBLIC)) {
      ourMap.putValue(type, XmlHighlighterColors.HTML_TAG);
    }

    ourMap.putValues(XML_PI_TARGET, Arrays.asList(XmlHighlighterColors.HTML_TAG, XmlHighlighterColors.HTML_TAG_NAME));

    ourMap.putValue(XML_CHAR_ENTITY_REF, XmlHighlighterColors.HTML_ENTITY_REFERENCE);
    ourMap.putValue(XML_ENTITY_REF_TOKEN, XmlHighlighterColors.HTML_ENTITY_REFERENCE);

    ourMap.putValue(XML_BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    registerAdditionalHighlighters(ourMap);
    EMBEDDED_HIGHLIGHTERS.addExtensionPointListener(new XmlFileHighlighter.EmbeddedTokenHighlighterExtensionPointListener(ourMap), null);
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new HtmlHighlightingLexer(FileTypeRegistry.getInstance().findFileTypeByName("CSS"));
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
    //noinspection SynchronizationOnGetClass,SynchronizeOnThis
    synchronized (getClass()) {
      return SyntaxHighlighterBase.pack(XmlHighlighterColors.HTML_CODE, ourMap.get(tokenType).toArray(TextAttributesKey.EMPTY_ARRAY));
    }
  }
}
