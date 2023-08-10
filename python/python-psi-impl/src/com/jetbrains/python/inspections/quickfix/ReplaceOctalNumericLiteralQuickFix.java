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
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import org.jetbrains.annotations.NotNull;

public class ReplaceOctalNumericLiteralQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.replace.octal.numeric.literal");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement numericLiteralExpression = descriptor.getPsiElement();
    if (numericLiteralExpression instanceof PyNumericLiteralExpression) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      String text = numericLiteralExpression.getText();
      final LanguageLevel level = LanguageLevel.forElement(numericLiteralExpression);
      numericLiteralExpression.replace(elementGenerator.createExpressionFromText(level, "0o" + text.substring(1)));
    }
  }
}
