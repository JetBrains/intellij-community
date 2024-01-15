// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.ConditionUtil;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyFile;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyNegateComparisonIntention extends PsiUpdateModCommandAction<PyBinaryExpression> {
  PyNegateComparisonIntention() {
    super(PyBinaryExpression.class);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.negate.comparison");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyBinaryExpression element) {
    if (!(context.file() instanceof PyFile)) {
      return null;
    }

    Pair<String, String> negation = ConditionUtil.findComparisonNegationOperators(element);
    if (negation != null) {
      return Presentation.of(PyPsiBundle.message("INTN.negate.comparison", negation.component1(), negation.component2()));
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyBinaryExpression element, @NotNull ModPsiUpdater updater) {
    ConditionUtil.negateComparisonExpression(context.project(), context.file(), element);
  }
}
