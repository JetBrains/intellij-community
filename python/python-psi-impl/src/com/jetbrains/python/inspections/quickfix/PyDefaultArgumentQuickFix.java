// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 * <p>
 * QuickFix to replace mutable default argument. For instance,
 * def foo(args=[]):
 * pass
 * replace with:
 * def foo(args=None):
 * if not args: args = []
 * pass
 */
@ApiStatus.Internal
public class PyDefaultArgumentQuickFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.default.argument");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyNamedParameter param = PsiTreeUtil.getParentOfType(element, PyNamedParameter.class);
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    assert param != null;
    final String defName = param.getName();
    if (function != null && defName != null) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final LanguageLevel languageLevel = LanguageLevel.forElement(function);

      final PyNamedParameter newParam = generator.createParameter(defName, PyNames.NONE, null, languageLevel);
      param.replace(newParam);

      final String conditionalText = "if " + defName + " is None:" +
                                     "\n\t" + defName + " = " + element.getText();
      final PyIfStatement conditionalAssignment = generator.createFromText(languageLevel, PyIfStatement.class, conditionalText);
      PyPsiRefactoringUtil.addElementToStatementList(conditionalAssignment, function.getStatementList(), true);
    }
  }
}
