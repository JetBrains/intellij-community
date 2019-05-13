// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyCellUtil {

  public static boolean hasCells(@NotNull PsiFile file) {
    PsiElement el = file.getFirstChild();
    while (el != null) {
      if (isBlockCell(el)) {
        return true;
      }
      el = el.getNextSibling();
    }
    return false;
  }

  @Nullable
  public static PsiElement getCellStart(@NotNull PsiElement element) {
    PsiElement el = element;
    while (el != null && !isBlockCell(el)) {
      el = PsiTreeUtil.prevLeaf(el);
    }
    if (el == null) {
      return element.getContainingFile().getFirstChild();
    }
    return PsiTreeUtil.nextLeaf(el);
  }

  public static boolean isBlockCell(PsiElement element) {
    return (element instanceof PsiComment) &&
           (element.getText().startsWith("# %%") || element.getText().startsWith("#%%") || element.getText().startsWith("# <codecell>")) ||
      element.getText().startsWith("# In[");
  }

  @NotNull
  public static String getCodeInCell(@Nullable PsiElement element) {
    StringBuilder text = new StringBuilder();
    while (element != null && !isBlockCell(element)) {
      text.append(element.getText());
      element = element.getNextSibling();
    }
    return StringUtil.trim(text.toString());
  }

  public static PsiElement findNextCell(PsiElement startElement) {
    PsiElement el = PsiTreeUtil.nextLeaf(PsiTreeUtil.getDeepestFirst(startElement));
    while (el != null) {
      if (isBlockCell(el)) {
        return el;
      }
      el = PsiTreeUtil.nextLeaf(el);
    }
    return null;
  }

  public static PsiElement findPrevCell(PsiElement startElement) {
    PsiElement el = PsiTreeUtil.prevLeaf(PsiTreeUtil.getDeepestFirst(startElement));
    while (el != null) {
      if (isBlockCell(el)) {
        return el;
      }
      el = PsiTreeUtil.prevLeaf(el);
    }
    return null;
  }
}
