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
import com.jetbrains.python.inspections.PySetFunctionToLiteralInspection;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 * Quick Fix to replace function call of built-in function "set" with
 * set literal if applicable
 */
public class ReplaceFunctionWithSetLiteralQuickFix implements LocalQuickFix {
  @Override
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.replace.function.set.with.literal");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElement[] elements = PySetFunctionToLiteralInspection.getSetCallArguments((PyCallExpression)descriptor.getPsiElement());
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiElement functionCall = descriptor.getPsiElement();
    StringBuilder str = new StringBuilder("{");
    for (int i = 0; i != elements.length; ++i) {
      PyElement e = elements[i];
      str.append(e.getText());
      if (i != elements.length-1)
        str.append(", ");
    }
    str.append("}");
    functionCall.replace(elementGenerator.createFromText(LanguageLevel.forElement(functionCall), PyExpressionStatement.class,
                                                             str.toString()).getExpression());
  }
}
