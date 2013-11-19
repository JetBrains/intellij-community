/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 20.02.2010
 * Time: 15:49:35
 */
public class ConvertDictCompQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.convert.dict.comp.to");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.dict.comp.expression");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (!LanguageLevel.forElement(element).isPy3K() && element instanceof PyDictCompExpression) {
      replaceComprehension(project, (PyDictCompExpression)element);
    }
  }

  private static void replaceComprehension(Project project, PyDictCompExpression expression) {
    List<ComprhForComponent> forComponents = expression.getForComponents();
    if (expression.getResultExpression() instanceof PyKeyValueExpression) {
      PyKeyValueExpression keyValueExpression = (PyKeyValueExpression)expression.getResultExpression();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      assert keyValueExpression.getValue() != null;
      expression.replace(elementGenerator.createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class,
                                                         "dict([(" + keyValueExpression.getKey().getText() + ", " +
                                                         keyValueExpression.getValue().getText() + ") for " +
                                                         forComponents.get(0).getIteratorVariable().getText() + " in " +
                                                         forComponents.get(0).getIteratedList().getText() + "])"));
    }
  }

}
