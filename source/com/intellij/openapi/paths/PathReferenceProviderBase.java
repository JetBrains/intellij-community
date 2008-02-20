/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.paths;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.jsp.jspJava.JspXmlTagBase;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class PathReferenceProviderBase implements PathReferenceProvider {

  public boolean createReferences(@NotNull final PsiElement psiElement, final @NotNull List<PsiReference> references, final boolean soft) {

    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(psiElement);
    assert manipulator != null;
    final TextRange range = manipulator.getRangeInElement(psiElement);
    int offset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    final String elementText = psiElement.getText();
    boolean dynamicContext = false;
    if (PsiUtil.isInJspFile(psiElement)) {
      final PsiElement[] children = psiElement.getChildren();
      for (PsiElement child : children) {
        if (child instanceof OuterLanguageElement || child instanceof JspXmlTagBase || child instanceof ELExpressionHolder) {
          final int i = child.getStartOffsetInParent();
          if (i == offset) {  // dynamic context?
            final PsiElement next = child.getNextSibling();
            if (next == null || !next.getText().startsWith("/")) {
              return false;
            }
            offset = next.getStartOffsetInParent();
            dynamicContext = true;
          } else {
            final int pos = getLastPosOfURL(elementText);
            if (pos == -1 || pos > i) {
              return false;
            }
            endOffset = pos;
            final String text = elementText.substring(offset, endOffset);
            return createReferences(psiElement, offset, text, references, soft || dynamicContext);
          }
        }
      }
    }
    final int pos = getLastPosOfURL(elementText);
    if (pos != -1 && pos < endOffset) {
      endOffset = pos;
    }

    final String text = elementText.substring(offset, endOffset);
    return createReferences(psiElement, offset, text, references, soft || dynamicContext);
  }

  public abstract boolean createReferences(@NotNull final PsiElement psiElement,
                                  final int offset,
                                  String text,
                                  final @NotNull List<PsiReference> references,
                                  final boolean soft);

  public static int getLastPosOfURL(@NotNull String url) {
    for (int i = 0; i < url.length(); i++) {
      switch (url.charAt(i)) {
        case '?':
        case '#':
          return i;
      }
    }
    return -1;
  }

}
