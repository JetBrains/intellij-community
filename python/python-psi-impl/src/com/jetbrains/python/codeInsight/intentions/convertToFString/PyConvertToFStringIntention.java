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

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public final class PyConvertToFStringIntention extends PsiUpdateModCommandAction<PsiElement> {
  PyConvertToFStringIntention() {
    super(PsiElement.class);
  }


  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.to.fstring.literal");
  }


  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PyFile) || LanguageLevel.forElement(context.file()).isOlderThan(LanguageLevel.PYTHON36)) return null;

    final BaseConvertToFStringProcessor processor = findSuitableProcessor(element);
    if (processor != null && processor.isRefactoringAvailable()) return super.getPresentation(context, element);
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final BaseConvertToFStringProcessor processor = findSuitableProcessor(element);
    assert processor != null;
    processor.doRefactoring();
  }

  @Nullable
  private static BaseConvertToFStringProcessor findSuitableProcessor(@NotNull PsiElement anchor) {
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
