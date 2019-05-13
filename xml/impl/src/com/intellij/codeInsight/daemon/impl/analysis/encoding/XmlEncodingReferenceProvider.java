/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class XmlEncodingReferenceProvider extends PsiReferenceProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.encoding.XmlEncodingReferenceProvider");
  @NonNls private static final String CHARSET_PREFIX = "charset=";

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
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
