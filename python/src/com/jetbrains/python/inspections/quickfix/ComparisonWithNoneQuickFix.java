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
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class ComparisonWithNoneQuickFix implements LocalQuickFix {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.replace.equality");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement instanceof PyBinaryExpression) {
      PyBinaryExpression binaryExpression = (PyBinaryExpression)problemElement;
      PyElementType operator = binaryExpression.getOperator();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      String temp;
      temp = (operator == PyTokenTypes.EQEQ) ? "is" : "is not";
      PyExpression expression = elementGenerator.createBinaryExpression(temp,
                                                                        binaryExpression.getLeftExpression(),
                                                                        binaryExpression.getRightExpression());
      binaryExpression.replace(expression);
    }
  }
}
