// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class ReplaceBuiltinsQuickFix implements LocalQuickFix {
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
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiElement importStatement = descriptor.getPsiElement();
    if (importStatement instanceof PyImportStatement) {
      for (PyImportElement importElement : ((PyImportStatement)importStatement).getImportElements()) {
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
