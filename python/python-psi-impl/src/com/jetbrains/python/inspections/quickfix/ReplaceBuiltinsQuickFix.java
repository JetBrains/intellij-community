// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class ReplaceBuiltinsQuickFix extends PsiUpdateModCommandQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyPsiBundle.message("INTN.convert.builtin.import");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.convert.builtin");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (element instanceof PyImportStatement) {
      for (PyImportElement importElement : ((PyImportStatement)element).getImportElements()) {
        PyReferenceExpression importReference = importElement.getImportReferenceExpression();
        if (importReference != null) {
          if ("__builtin__".equals(importReference.getName())) {
            importReference.replace(elementGenerator.createExpressionFromText(LanguageLevel.getDefault(), "builtins"));
          }
          if ("builtins".equals(importReference.getName())) {
            importReference.replace(elementGenerator.createExpressionFromText(LanguageLevel.getDefault(), "__builtin__"));
          }
        }
      }
    }
  }
}
