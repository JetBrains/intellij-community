// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ArrayUtilRt;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public final class XmlTagUtil extends XmlTagUtilBase {
  private static final Object2IntMap<String> ourCharacterEntities = new Object2IntOpenHashMap<>();

  static {
    ourCharacterEntities.put("lt", '<');
    ourCharacterEntities.put("gt", '>');
    ourCharacterEntities.put("apos", '\'');
    ourCharacterEntities.put("quot", '\"');
    ourCharacterEntities.put("nbsp", '\u00a0');
    ourCharacterEntities.put("amp", '&');
  }

  /**
   * if text contains XML-sensitive characters (<,>), quote text with ![CDATA[ ... ]]
   *
   * @param text
   * @return quoted text
   */
  public static String getCDATAQuote(String text) {
    if (text == null) return null;
    String offensiveChars = "<>&\n";
    final int textLength = text.length();
    if (textLength > 0 && (Character.isWhitespace(text.charAt(0)) || Character.isWhitespace(text.charAt(textLength - 1)))) {
      return "<![CDATA[" + text + "]]>";
    }
    for (int i = 0; i < offensiveChars.length(); i++) {
      char c = offensiveChars.charAt(i);
      if (text.indexOf(c) != -1) {
        return "<![CDATA[" + text + "]]>";
      }
    }
    return text;
  }

  public static String getInlineQuote(String text) {
    if (text == null) return null;
    String offensiveChars = "<>&";
    for (int i = 0; i < offensiveChars.length(); i++) {
      char c = offensiveChars.charAt(i);
      if (text.indexOf(c) != -1) {
        return "<![CDATA[" + text + "]]>";
      }
    }
    return text;
  }


  public static CharSequence composeTagText(@NonNls String tagName, @NonNls String tagValue) {
    StringBuilder builder = new StringBuilder();
    builder.append('<').append(tagName);
    if (StringUtil.isEmpty(tagValue)) {
      builder.append("/>");
    }
    else {
      builder.append('>').append(getCDATAQuote(tagValue)).append("</").append(tagName).append('>');
    }
    return builder;
  }

  public static String[] getCharacterEntityNames() {
    return ArrayUtilRt.toStringArray(ourCharacterEntities.keySet());
  }

  public static char getCharacterByEntityName(String entityName) {
    return (char)ourCharacterEntities.getInt(entityName);
  }

  @Nullable
  public static XmlToken getStartTagNameElement(@NotNull XmlTag tag) {
    final ASTNode node = tag.getNode();
    if (node == null) return null;

    ASTNode current = node.getFirstChildNode();
    IElementType elementType;
    while (current != null
           && (elementType = current.getElementType()) != XmlTokenType.XML_NAME
           && elementType != XmlTokenType.XML_TAG_NAME) {
      current = current.getTreeNext();
    }
    return current == null ? null : (XmlToken)current.getPsi();
  }

  @Nullable
  public static XmlToken getEndTagNameElement(@NotNull XmlTag tag) {
    final ASTNode node = tag.getNode();
    if (node == null) return null;

    ASTNode current = node.getLastChildNode();
    ASTNode prev = current;

    while (current != null) {
      final IElementType elementType = prev.getElementType();
      if ((elementType == XmlTokenType.XML_NAME || elementType == XmlTokenType.XML_TAG_NAME) &&
          current.getElementType() == XmlTokenType.XML_END_TAG_START) {
        return (XmlToken)prev.getPsi();
      }

      prev = current;
      current = TemplateLanguageUtil.getSameLanguageTreePrev(current);
    }
    return null;
  }

  @NotNull
  public static TextRange getTrimmedValueRange(final @NotNull XmlTag tag) {
    XmlTagValue tagValue = tag.getValue();
    final String text = tagValue.getText();
    final String trimmed = text.trim();
    final int index = text.indexOf(trimmed);
    final int startOffset = tagValue.getTextRange().getStartOffset() - tag.getTextRange().getStartOffset() + index;
    return new TextRange(startOffset, startOffset + trimmed.length());
  }

  @Nullable
  public static TextRange getStartTagRange(@NotNull XmlTag tag) {
    XmlToken tagName = getStartTagNameElement(tag);
    return getTagRange(tagName, XmlTokenType.XML_START_TAG_START);
  }


  @Nullable
  public static TextRange getEndTagRange(@NotNull XmlTag tag) {
    XmlToken tagName = getEndTagNameElement(tag);

    return getTagRange(tagName, XmlTokenType.XML_END_TAG_START);
  }

  @Nullable
  private static TextRange getTagRange(@Nullable XmlToken tagName, IElementType tagStart) {
    if (tagName == null) {
      return null;
    }
    PsiElement s = tagName.getPrevSibling();

    while (s != null && s.getNode().getElementType() != tagStart) {
      s = s.getPrevSibling();
    }

    PsiElement f = tagName.getNextSibling();

    while (f != null &&
           !(f.getNode().getElementType() == XmlTokenType.XML_TAG_END ||
             f.getNode().getElementType() == XmlTokenType.XML_EMPTY_ELEMENT_END)) {
      f = f.getNextSibling();
    }
    if (s != null && f != null) {
      return new TextRange(s.getTextRange().getStartOffset(), f.getTextRange().getEndOffset());
    }
    return null;
  }
}
