// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.PySetFunctionToLiteralInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpressionStatement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 * Quick Fix to replace function call of built-in function "set" with
 * set literal if applicable
 */
@ApiStatus.Internal
public class ReplaceFunctionWithSetLiteralQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.replace.function.set.with.literal");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyElement[] elements = PySetFunctionToLiteralInspection.getSetCallArguments((PyCallExpression)element);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    StringBuilder str = new StringBuilder("{");
    for (int i = 0; i != elements.length; ++i) {
      PyElement e = elements[i];
      str.append(e.getText());
      if (i != elements.length - 1) {
        str.append(", ");
      }
    }
    str.append("}");
    element.replace(elementGenerator.createFromText(LanguageLevel.forElement(element), PyExpressionStatement.class,
                                                    str.toString()).getExpression());
  }
}
