/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public final class PyMagicLiteralRenameHandler implements RenameHandler {
  /**
   * @return string literal under data context or null if not a literal.
   * This method is fast, so it can safely be used at {@link #isAvailableOnDataContext(DataContext)}
   */
  @Nullable
  private static PyStringLiteralExpression getStringLiteral(@NotNull final DataContext dataContext) {
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

  @Nullable
  private static PsiElement getElement(PsiFile file, Editor editor) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getCurrentCaret().getOffset());
    if (element instanceof PyStringLiteralExpression) {
      return element;
    }
    return PsiTreeUtil.getParentOfType(element, StringLiteralExpression.class);
  }

  @Override
  public boolean isAvailableOnDataContext(@NotNull final DataContext dataContext) {
    return getStringLiteral(dataContext) != null;
  }

  @Override
  public boolean isRenaming(@NotNull final DataContext dataContext) {
    final PyStringLiteralExpression literal = getStringLiteral(dataContext);
    return !((literal == null) || !PyMagicLiteralTools.isMagicLiteral(literal));
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
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}
