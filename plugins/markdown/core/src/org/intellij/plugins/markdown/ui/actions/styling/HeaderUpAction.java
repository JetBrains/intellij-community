// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader;
import org.jetbrains.annotations.NotNull;

public class HeaderUpAction extends MarkdownHeaderAction {
  @NotNull
  @Override
  protected Function<Integer, Integer> getLevelFunction() {
    return integer -> integer - 1;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull PsiFile psiFile, int selectionStart, int selectionEnd) {
    final var parent = findParent(psiFile, selectionStart, selectionEnd);
    if (parent == null) {
      return false;
    }
    final var header = PsiTreeUtil.getParentOfType(parent, MarkdownHeader.class, false);
    return header != null;
  }
}
