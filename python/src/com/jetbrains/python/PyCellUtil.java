// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyCellUtil {

  public static final class CellRange {
    public final TextRange textRange;
    public final Boolean lastCell;

    public CellRange(TextRange textRange, Boolean lastCell) {
      this.textRange = textRange;
      this.lastCell = lastCell;
    }
  }

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

  public static @Nullable PsiElement getCellStart(@NotNull PsiElement element) {
    PsiElement el = element;
    while (el != null && !isBlockCell(el)) {
      el = PsiTreeUtil.prevLeaf(el);
    }
    if (el == null) {
      return element.getContainingFile().getFirstChild();
    }
    return PsiTreeUtil.nextLeaf(el);
  }

  public static final String[] CELL_SEPARATORS = {
    "# %%",
    "#%%",
    "# <codecell>",
    "# In[",
    "# COMMAND ----------" // Databricks py-notebooks separator.
  };

  public static boolean isBlockDefinition(String text) {
    for (String separator : CELL_SEPARATORS) {
      if (text.startsWith(separator)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isBlockCell(PsiElement element) {
    return (element instanceof PsiComment) && isBlockDefinition(element.getText());
  }

  public static @NotNull String getCodeInCell(@Nullable PsiElement element) {
    StringBuilder text = new StringBuilder();
    while (element != null && !isBlockCell(element)) {
      text.append(element.getText());
      element = element.getNextSibling();
    }
    return StringUtil.trim(text.toString());
  }

  public static CellRange getCellTextRangeIncludingSeparators(@NotNull PsiElement element) {
    PsiElement el = element;
    while (el != null && !isBlockCell(el)) {
      el = PsiTreeUtil.prevLeaf(el);
    }

    int startOffset = el == null ? element.getContainingFile().getFirstChild().getTextOffset() : el.getTextRange().getStartOffset();

    PsiElement nextCell = findNextCell(element);
    int endOffset = nextCell == null ? element.getContainingFile().getLastChild().getTextRange().getEndOffset()
                                     : nextCell.getTextRange().getStartOffset();

    return new CellRange(new TextRange(startOffset, endOffset), nextCell == null);
  }

  public static @Nullable PsiElement findNextCell(PsiElement startElement) {
    PsiElement el = PsiTreeUtil.nextLeaf(PsiTreeUtil.getDeepestFirst(startElement));
    while (el != null) {
      if (isBlockCell(el)) {
        return el;
      }
      el = PsiTreeUtil.nextLeaf(el);
    }
    return null;
  }

  public static @Nullable PsiElement findSeparatorOfPrevCell(PsiElement startElement) {
    PsiElement currentCellSeparator = PsiTreeUtil.prevLeaf(PsiTreeUtil.getDeepestFirst(startElement));
    while (currentCellSeparator != null && !isBlockCell(currentCellSeparator)) {
      currentCellSeparator = PsiTreeUtil.prevLeaf(currentCellSeparator);
    }

    if (currentCellSeparator == null) return null;

    return findPrevCell(currentCellSeparator);
  }

  // Not a prev cell, but only a cell separator that is over the startElement (it could be in the same cell).
  public static @Nullable PsiElement findPrevCell(PsiElement startElement) {
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