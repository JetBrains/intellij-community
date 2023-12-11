/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.intentions.convertToFString;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.intentions.PyBaseIntentionAction;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public final class PyConvertToFStringIntention extends PyBaseIntentionAction {
  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.to.fstring.literal");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile) || LanguageLevel.forElement(file).isOlderThan(LanguageLevel.PYTHON36)) return false;

    final BaseConvertToFStringProcessor processor = findSuitableProcessor(editor, file);
    return processor != null && processor.isRefactoringAvailable();
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final BaseConvertToFStringProcessor processor = findSuitableProcessor(editor, file);
    assert processor != null;
    processor.doRefactoring();
  }

  @Nullable
  private static BaseConvertToFStringProcessor findSuitableProcessor(@NotNull Editor editor, @NotNull PsiFile file) {
    final PsiElement anchor = file.findElementAt(editor.getCaretModel().getOffset());
    if (anchor == null) return null;

    final PyBinaryExpression binaryExpr = PsiTreeUtil.getParentOfType(anchor, PyBinaryExpression.class);
    if (binaryExpr != null && binaryExpr.getOperator() == PyTokenTypes.PERC) {
      final PyStringLiteralExpression pyString = as(binaryExpr.getLeftExpression(), PyStringLiteralExpression.class);
      if (pyString != null) {
        return new OldStyleConvertToFStringProcessor(pyString);
      }
    }

    final PyCallExpression callExpr = PsiTreeUtil.getParentOfType(anchor, PyCallExpression.class);
    if (callExpr != null) {
      final PyReferenceExpression callee = as(callExpr.getCallee(), PyReferenceExpression.class);
      if (callee != null && PyNames.FORMAT.equals(callee.getName())) {
        final PyStringLiteralExpression pyString = as(callee.getQualifier(), PyStringLiteralExpression.class);
        if (pyString != null) {
          return new NewStyleConvertToFStringProcessor(pyString);
        }
      }
    }
    return null;
  }
}
