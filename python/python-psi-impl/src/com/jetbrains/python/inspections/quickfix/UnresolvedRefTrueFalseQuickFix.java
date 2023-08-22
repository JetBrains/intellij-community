// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 * <p>
 * QuickFix to replace true with True, false with False
 */
public class UnresolvedRefTrueFalseQuickFix implements LocalQuickFix {
  String newName;

  public UnresolvedRefTrueFalseQuickFix(@NotNull String oldName) {
    newName = StringUtil.capitalize(oldName);
  }

  @Override
  @NotNull
  public String getName() {
    return PyPsiBundle.message("QFIX.replace.with.true.or.false", newName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.replace.with.true.or.false");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    final PsiElement element = descriptor.getPsiElement();
    if (element != null) {
      PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), newName);
      element.replace(expression);
    }
  }
}
