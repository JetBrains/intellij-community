// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.ConditionUtil;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyFile;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyNegateComparisonIntention extends PsiUpdateModCommandAction<PsiElement> {
  PyNegateComparisonIntention() {
    super(PsiElement.class);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.negate.comparison");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    Pair<String, String> negation = ConditionUtil.findComparisonNegationOperators(binaryExpression);
    if (negation != null) {
      return Presentation.of(PyPsiBundle.message("INTN.negate.comparison", negation.component1(), negation.component2()));
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    ConditionUtil.negateComparisonExpression(context.project(), context.file(), binaryExpression);
  }
}
