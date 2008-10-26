/*
 * User: anna
 * Date: 01-Feb-2008
 */
package com.intellij.codeInsight.hint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class XmlImplementationTextSelectioner implements ImplementationTextSelectioner {
  private static final Logger LOG = Logger.getInstance("#" + XmlImplementationTextSelectioner.class.getName());

  public int getTextStartOffset(@NotNull final PsiElement parent) {
    return parent.getTextRange().getStartOffset();
  }

  public int getTextEndOffset(@NotNull PsiElement element) {
    if (element instanceof XmlAttributeValue) {
      final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);// for convenience
      if (xmlTag != null) return xmlTag.getTextRange().getEndOffset();
      LOG.assertTrue(false);
    }
    return element.getTextRange().getEndOffset();
  }
}