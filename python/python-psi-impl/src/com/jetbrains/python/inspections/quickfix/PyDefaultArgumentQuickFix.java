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
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to replace mutable default argument. For instance,
 * def foo(args=[]):
     pass
 * replace with:
 * def foo(args=None):
     if not args: args = []
     pass
 */
public class PyDefaultArgumentQuickFix implements LocalQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.default.argument");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement defaultValue = descriptor.getPsiElement();
    final PyNamedParameter param = PsiTreeUtil.getParentOfType(defaultValue, PyNamedParameter.class);
    final PyFunction function = PsiTreeUtil.getParentOfType(defaultValue, PyFunction.class);
    assert param != null;
    final String defName = param.getName();
    if (function != null && defName != null) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(project);
      final LanguageLevel languageLevel = LanguageLevel.forElement(function);
      
      final PyNamedParameter newParam = generator.createParameter(defName, PyNames.NONE, null, languageLevel);
      param.replace(newParam);

      final String conditionalText = "if " + defName + " is None:" +
                                     "\n\t" + defName + " = " + defaultValue.getText();
      final PyIfStatement conditionalAssignment = generator.createFromText(languageLevel, PyIfStatement.class, conditionalText);
      PyPsiRefactoringUtil.addElementToStatementList(conditionalAssignment, function.getStatementList(), true);
    }
  }
}
