// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConvertDictCompQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.convert.dict.comp.to");
  }

  @Override
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
    if (expression.getResultExpression() instanceof PyKeyValueExpression) {
      final PyKeyValueExpression keyValueExpression = (PyKeyValueExpression)expression.getResultExpression();
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      assert keyValueExpression.getValue() != null;

      final List<PyComprehensionComponent> components = expression.getComponents();
      final StringBuilder replacement = new StringBuilder("dict([(" + keyValueExpression.getKey().getText() + ", " +
                                                            keyValueExpression.getValue().getText() + ")");
      int slashNum = 1;
      for (PyComprehensionComponent component : components) {
        if (component instanceof PyComprehensionForComponent) {
          replacement.append("for ");
          replacement.append(((PyComprehensionForComponent)component).getIteratorVariable().getText());
          replacement.append(" in ");
          replacement.append(((PyComprehensionForComponent)component).getIteratedList().getText());
          replacement.append(" ");
        }
        if (component instanceof PyComprehensionIfComponent) {
          final PyExpression test = ((PyComprehensionIfComponent)component).getTest();
          if (test != null) {
            replacement.append("if ");
            replacement.append(test.getText());
            replacement.append(" ");
          }
        }
        for (int i = 0; i != slashNum; ++i)
          replacement.append("\t");
        ++slashNum;
      }
      replacement.append("])");

      expression.replace(elementGenerator.createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, replacement.toString()));
    }
  }

}
