// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.magicLiteral;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyMagicLiteralRenameHandler implements RenameHandler {
  /**
   * @return string literal under data context or null if not a literal.
   * This method is fast, so it can safely be used at {@link #isAvailableOnDataContext(DataContext)}
   */
  private static @Nullable PyStringLiteralExpression getStringLiteral(final @NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return null;
    }

    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (file == null) {
      return null;
    }

    final PsiElement element = getElement(file, editor);
    if (element instanceof PyStringLiteralExpression) {
      return (PyStringLiteralExpression)element;
    }
    return null;
  }

  private static @Nullable PsiElement getElement(PsiFile file, Editor editor) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getCurrentCaret().getOffset());
    StringLiteralExpression stringLiteral = PsiTreeUtil.getParentOfType(element, StringLiteralExpression.class, false,
                                                                        PyFStringFragment.class);
    if (stringLiteral instanceof PyStringLiteralExpression && ((PyStringLiteralExpression)stringLiteral).isInterpolated()) {
      return null;
    }
    return stringLiteral;
  }

  @Override
  public boolean isAvailableOnDataContext(final @NotNull DataContext dataContext) {
    return getStringLiteral(dataContext) != null;
  }

  @Override
  public boolean isRenaming(final @NotNull DataContext dataContext) {
    final PyStringLiteralExpression literal = getStringLiteral(dataContext);
    return !((literal == null) || !PyMagicLiteralTools.couldBeMagicLiteral(literal));
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = getElement(file, editor);
    if (element == null) {
      return;
    }
    RenameDialog.showRenameDialog(dataContext, new RenameDialog(project, element, null, editor));
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}
