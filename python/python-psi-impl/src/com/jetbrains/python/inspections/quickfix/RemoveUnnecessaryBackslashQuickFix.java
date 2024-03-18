// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.editor.PyEditorHandlerConfig;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to remove all unnecessary backslashes in expression
 */
public class RemoveUnnecessaryBackslashQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.remove.unnecessary.backslash");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = PsiTreeUtil.getParentOfType(element, PyEditorHandlerConfig.IMPLICIT_WRAP_CLASSES);
      removeBackSlash(parent);
  }

  public static void removeBackSlash(PsiElement parent) {
    if (parent != null) {
      Stack<PsiElement> stack = new Stack<>();
      if (parent instanceof PyParenthesizedExpression)
        stack.push(((PyParenthesizedExpression)parent).getContainedExpression());
      else
        stack.push(parent);
      while (!stack.isEmpty()) {
        PsiElement el = stack.pop();
        PsiWhiteSpace[] children = PsiTreeUtil.getChildrenOfType(el, PsiWhiteSpace.class);
        if (children != null) {
          for (PsiWhiteSpace ws : children) {
            if (ws.getText().contains("\\")) {
              ws.delete();
            }
          }
        }
        for (PsiElement psiElement : el.getChildren()) {
          stack.push(psiElement);
        }
      }
    }
  }
}
