// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader;
import org.jetbrains.annotations.NotNull;

public class HeaderDownAction extends MarkdownHeaderAction {
  @Override
  protected @NotNull Function<Integer, Integer> getLevelFunction() {
    return integer -> integer + 1;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull PsiFile psiFile, int selectionStart, int selectionEnd) {
    final var parent = findParent(psiFile, selectionStart, selectionEnd);
    if (parent == null) {
      return false;
    }
    final var header = PsiTreeUtil.getParentOfType(parent, MarkdownHeader.class, false);
    if (header != null) {
      return header.getLevel() != 6;
    }
    return true;
  }
}
