// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XmlEncodingReferenceProvider extends PsiReferenceProvider {
  private static final Logger LOG = Logger.getInstance(XmlEncodingReferenceProvider.class);
  private static final TokenSet ATTRIBUTE_VALUE_STD_TOKENS = TokenSet.create(
    XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER,
    XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER,
    XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN
  );
  private static final @NonNls String CHARSET_PREFIX = "charset=";

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, final @NotNull ProcessingContext context) {
    LOG.assertTrue(element instanceof XmlAttributeValue);
    XmlAttributeValue value = (XmlAttributeValue)element;

    return new PsiReference[]{new XmlEncodingReference(value, value.getValue(), xmlAttributeValueRange(value), 0)};
  }

  protected static TextRange xmlAttributeValueRange(final XmlAttributeValue xmlAttributeValue) {
    ASTNode valueNode = XmlChildRole.ATTRIBUTE_VALUE_VALUE_FINDER.findChild(xmlAttributeValue.getNode());
    PsiElement toHighlight = valueNode == null ? xmlAttributeValue : valueNode.getPsi();
    TextRange childRange = toHighlight.getTextRange();
    TextRange range = xmlAttributeValue.getTextRange();
    return childRange.shiftRight(-range.getStartOffset());
  }

  public static PsiReference[] extractFromContentAttribute(final XmlAttributeValue value) {
    boolean hasNonStandardTokens =
      ContainerUtil.exists(value.getChildren(), ch -> !ATTRIBUTE_VALUE_STD_TOKENS.contains(ch.getNode().getElementType()));
    if (hasNonStandardTokens) return PsiReference.EMPTY_ARRAY;
    String text = value.getValue();
    int start = text.indexOf(CHARSET_PREFIX);
    if (start != -1) {
      start += CHARSET_PREFIX.length();
      int end = text.indexOf(';', start);
      if (end == -1) end = text.length();
      String charsetName = text.substring(start, end);
      TextRange textRange = new TextRange(start, end).shiftRight(xmlAttributeValueRange(value).getStartOffset());
      return new PsiReference[]{new XmlEncodingReference(value, charsetName, textRange, 0)};
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
