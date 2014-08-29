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
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
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
  public String getName() {
    return PyBundle.message("QFIX.default.argument");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement defaultValue = descriptor.getPsiElement();
    PyNamedParameter param = PsiTreeUtil.getParentOfType(defaultValue, PyNamedParameter.class);
    PyFunction function = PsiTreeUtil.getParentOfType(defaultValue, PyFunction.class);
    assert param != null;
    String defName = param.getName();
    if (function != null) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      PyStatementList list = function.getStatementList();
      PyParameterList paramList = function.getParameterList();

      final StringBuilder functionText = new StringBuilder("def foo(");
      int size = paramList.getParameters().length;
      for (int i = 0; i != size; ++i) {
        PyParameter p = paramList.getParameters()[i];
        if (p == param)
          functionText.append(defName).append("=None");
        else
          functionText.append(p.getText());
        if (i != size-1)
          functionText.append(", ");
      }
      functionText.append("):\n\tif not ").append(defName).append(":\n\t\t").append(defName).append(" = ").append(defaultValue.getText());
      final PyStatement[] statements = list.getStatements();
      PyStatement firstStatement = statements.length > 0 ? statements[0] : null;
      PyFunction newFunction = elementGenerator.createFromText(LanguageLevel.forElement(function), PyFunction.class,
                                                               functionText.toString());
      if (firstStatement == null) {
        function.replace(newFunction);
      }
      else {
        final PyStatement ifStatement = newFunction.getStatementList().getStatements()[0];
        PyStringLiteralExpression docString = function.getDocStringExpression();
        if (docString != null)
          list.addAfter(ifStatement, firstStatement);
        else {
          list.addBefore(ifStatement, firstStatement);
        }
        paramList.replace(elementGenerator.createFromText(LanguageLevel.forElement(defaultValue),
                                                          PyFunction.class, functionText.toString()).getParameterList());
      }
    }
  }
}
