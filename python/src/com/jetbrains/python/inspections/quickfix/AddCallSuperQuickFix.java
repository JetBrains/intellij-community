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
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * For:
 * class B(A):
 * def __init__(self):
 * A.__init__(self)           #  inserted
 * print "Constructor B was called"
 * <p/>
 * User: catherine
 */
public class AddCallSuperQuickFix implements LocalQuickFix {

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.add.super");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyFunction.class);
    if (problemFunction == null) return;
    final StringBuilder superCall = new StringBuilder();
    final PyClass klass = problemFunction.getContainingClass();
    if (klass == null) return;
    final PyClass[] superClasses = klass.getSuperClasses();
    if (superClasses.length == 0) return;

    final PyClass superClass = superClasses[0];
    final PyFunction superInit = superClass.findMethodByName(PyNames.INIT, true);
    if (superInit == null) return;
    boolean addComma = true;
    if (klass.isNewStyleClass()) {
      addComma = false;
      if (LanguageLevel.forElement(klass).isPy3K()) {
        superCall.append("super().__init__(");
      }
      else {
        superCall.append("super(").append(klass.getName()).append(", self).__init__(");
      }
    }
    else {
      superCall.append(superClass.getName());
      superCall.append(".__init__(self");
    }
    final StringBuilder newFunction = new StringBuilder("def __init__(self");

    buildParameterList(problemFunction, superInit, superCall, newFunction, addComma);

    superCall.append(")");
    final PyStatementList statementList = problemFunction.getStatementList();
    PyExpression docstring = null;
    final PyStatement[] statements = statementList.getStatements();
    if (statements.length != 0 && statements[0] instanceof PyExpressionStatement) {
      final PyExpressionStatement st = (PyExpressionStatement)statements[0];
      if (st.getExpression() instanceof PyStringLiteralExpression) {
        docstring = st.getExpression();
      }
    }

    newFunction.append("):\n\t");
    if (docstring != null) {
      newFunction.append(docstring.getText()).append("\n\t");
    }
    newFunction.append(superCall).append("\n\t");
    boolean first = true;
    for (PyStatement statement : statements) {
      if (first && docstring != null || statement instanceof PyPassStatement) {
        first = false;
        continue;
      }
      newFunction.append(statement.getText()).append("\n\t");
    }

    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    problemFunction.replace(generator.createFromText(LanguageLevel.forElement(problemFunction), PyFunction.class, newFunction.toString()));
  }

  private static void buildParameterList(@NotNull PyFunction problemFunction,
                                         @NotNull PyFunction superInit,
                                         @NotNull StringBuilder superCall,
                                         @NotNull StringBuilder newFunction,
                                         boolean addComma) {
    final PyParameter[] parameters = problemFunction.getParameterList().getParameters();
    final List<String> problemParams = new ArrayList<String>();
    final List<String> functionParams = new ArrayList<String>();
    String starName = null;
    String doubleStarName = null;
    for (int i = 1; i != parameters.length; i++) {
      final PyParameter p = parameters[i];
      functionParams.add(p.getName());
      if (p.getText().startsWith("**")) {
        doubleStarName = p.getText();
        continue;
      }
      if (p.getText().startsWith("*")) {
        starName = p.getText();
        continue;
      }
      if (p.getDefaultValue() != null) {
        problemParams.add(p.getText());
        continue;
      }
      newFunction.append(",").append(p.getText());
    }

    addParametersFromSuper(superInit, superCall, newFunction, problemParams, functionParams, starName, doubleStarName, addComma);
  }

  private static void addParametersFromSuper(@NotNull PyFunction superInit,
                                             @NotNull StringBuilder superCall,
                                             @NotNull StringBuilder newFunction,
                                             @NotNull List<String> problemParams,
                                             @NotNull List<String> functionParams,
                                             @Nullable String starName,
                                             @Nullable String doubleStarName,
                                             boolean addComma) {
    final PyParameterList paramList = superInit.getParameterList();
    final PyParameter[] parameters = paramList.getParameters();
    boolean addDouble = false;
    boolean addStar = false;
    for (int i = 1; i != parameters.length; i++) {
      final PyParameter p = parameters[i];
      if (p.getDefaultValue() != null) continue;
      final String param = p.getName();
      final String paramText = p.getText();
      if (paramText.startsWith("**")) {
        addDouble = true;
        if (doubleStarName == null) {
          doubleStarName = p.getText();
        }
        continue;
      }
      if (paramText.startsWith("*")) {
        addStar = true;
        if (starName == null) {
          starName = p.getText();
        }
        continue;
      }
      if (addComma) {
        superCall.append(",");
      }
      superCall.append(param);
      if (!functionParams.contains(param)) {
        newFunction.append(",").append(param);
      }
      addComma = true;
    }
    for (String p : problemParams) {
      newFunction.append(",").append(p);
    }
    if (starName != null) {
      newFunction.append(",").append(starName);
      if (addStar) {
        if (addComma) superCall.append(",");
        superCall.append(starName);
        addComma = true;
      }
    }
    if (doubleStarName != null) {
      newFunction.append(",").append(doubleStarName);
      if (addDouble) {
        if (addComma) superCall.append(",");
        superCall.append(doubleStarName);
      }
    }
  }
}
