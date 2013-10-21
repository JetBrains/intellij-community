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
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * For:
 * class B(A):
 *   def __init__(self):
 *       A.__init__(self)           #  inserted
 *       print "Constructor B was called"
 *
 * User: catherine
 */
public class AddCallSuperQuickFix implements LocalQuickFix {
  private final PyClass mySuper;
  private String mySuperName;

  public AddCallSuperQuickFix(PyClass superClass, String superName) {
    mySuper = superClass;
    mySuperName = superName;
  }

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
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyFunction.class);
    if (problemFunction == null) return;
    PyFunction superInit = mySuper.findMethodByName(PyNames.INIT, true);
    if (superInit == null) return;
    StringBuilder superCall = new StringBuilder();
    PyClass klass = problemFunction.getContainingClass();
    boolean addComma = true;
    if (klass != null && klass.isNewStyleClass()) {
      addComma = false;
      if (LanguageLevel.forElement(klass).isPy3K())
        superCall.append("super().__init__(");
      else
        superCall.append("super(").append(klass.getName()).append(", self).__init__(");
    }
    else {
      superCall.append(mySuperName);
      superCall.append(".__init__(self");
    }
    StringBuilder newFunction = new StringBuilder("def __init__(self");

    buildParameterList(problemFunction, superInit, superCall, newFunction, addComma);

    superCall.append(")");
    final PyStatementList statementList = problemFunction.getStatementList();
    PyExpression docstring = null;
    final PyStatement[] statements = statementList == null ? new PyStatement[0] : statementList.getStatements();
    if (statements.length != 0 && statements[0] instanceof PyExpressionStatement) {
      PyExpressionStatement st = (PyExpressionStatement)statements[0];
      if (st.getExpression() instanceof PyStringLiteralExpression)
        docstring = st.getExpression();
    }

    newFunction.append("):\n\t");
    if (docstring != null)
      newFunction.append(docstring.getText()).append("\n\t");
    newFunction.append(superCall).append("\n\t");
    boolean first = true;
    for (PyStatement statement : statements) {
      if (first && docstring != null || statement instanceof PyPassStatement) {
        first = false;
        continue;
      }
      newFunction.append(statement.getText()).append("\n\t");
    }

    problemFunction.replace(
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.forElement(problemFunction), PyFunction.class,
                                                             newFunction.toString()));
  }

  private static void buildParameterList(@NotNull final PyFunction problemFunction,
                                            @NotNull final PyFunction superInit,
                                            @NotNull final StringBuilder superCall,
                                            @NotNull final StringBuilder newFunction, boolean addComma) {
    final PyParameter[] parameters = problemFunction.getParameterList().getParameters();
    final List<String> problemParams = new ArrayList<String>();
    final List<String> functionParams = new ArrayList<String>();
    String starName = null;
    String doubleStarName = null;
    for (int i = 1; i != parameters.length; i++) {
      PyParameter p = parameters[i];
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

    addParametersFromSuper(superInit, superCall, newFunction, addComma, problemParams, functionParams, starName, doubleStarName);
  }

  private static void addParametersFromSuper(@NotNull final PyFunction superInit, @NotNull final StringBuilder superCall,
                                             @NotNull final StringBuilder newFunction, boolean addComma,
                                             @NotNull final List<String> problemParams, @NotNull final List<String> functionParams,
                                             String starName, String doubleStarName) {
    final PyParameterList paramList = superInit.getParameterList();
    PyParameter[] parameters = paramList.getParameters();
    boolean addDouble = false;
    boolean addStar = false;
    for (int i = 1; i != parameters.length; i++) {
      PyParameter p = parameters[i];
      if (p.getDefaultValue() != null) continue;
      String param;
      param = p.getText();
      if (param.startsWith("**")) {
        addDouble = true;
        if (doubleStarName == null)
          doubleStarName = p.getText();
        continue;
      }
      if (param.startsWith("*")) {
        addStar = true;
        if (starName == null)
          starName = p.getText();
        continue;
      }
      if (addComma)
        superCall.append(",");
      superCall.append(param);
      if (!functionParams.contains(param))
        newFunction.append(",").append(param);
      addComma = true;
    }
    for(String p : problemParams)
      newFunction.append(",").append(p);
    if (addStar) {
      newFunction.append(",").append(starName);
      if (addComma) superCall.append(",");
      superCall.append(starName);
      addComma = true;
    }
    if (addDouble) {
      if (addComma) superCall.append(",");
      superCall.append(doubleStarName);
      newFunction.append(",").append(doubleStarName);
    }
  }
}
