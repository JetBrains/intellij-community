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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

public class ReplaceBuiltinsQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.convert.builtin.import");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.builtin");
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
