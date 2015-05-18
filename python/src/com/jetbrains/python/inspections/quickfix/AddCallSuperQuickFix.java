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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
    final boolean addComma;
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
      addComma = true;
      superCall.append(superClass.getName());
      superCall.append(".__init__(self");
    }
    final StringBuilder newFunction = new StringBuilder("def __init__(self");

    final Couple<List<String>> couple = buildNewFunctionParamsAndSuperInitCallArgs(problemFunction, superInit);

    final List<String> newParameters = couple.getFirst();
    if (!newParameters.isEmpty()) {
      newFunction.append(", ");
    }
    StringUtil.join(newParameters, ", ", newFunction);
    newFunction.append(")");
    if (problemFunction.getAnnotation() != null) {
      newFunction.append(problemFunction.getAnnotation().getText());
    }
    newFunction.append(":\n\t");

    final List<String> superCallArguments = couple.getSecond();
    if (addComma && !superCallArguments.isEmpty()) {
      superCall.append(", ");
    }
    StringUtil.join(superCallArguments, ", ", superCall);
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

  @NotNull
  private static Couple<List<String>> buildNewFunctionParamsAndSuperInitCallArgs(@NotNull PyFunction origInit,
                                                                                 @NotNull PyFunction superInit) {
    final List<String> newFunctionParams = new ArrayList<String>();
    final List<String> superCallArgs = new ArrayList<String>();

    final ParametersInfo origInfo = new ParametersInfo(origInit.getParameterList());
    final ParametersInfo superInfo = new ParametersInfo(superInit.getParameterList());

    // Required parameters (not-keyword)
    for (PyParameter param : origInfo.getRequiredParameters()) {
      newFunctionParams.add(param.getText());
    }
    for (PyParameter param : superInfo.getRequiredParameters()) {
      if (!origInfo.getAllParameterNames().contains(param.getName())) {
        newFunctionParams.add(param.getText());
      }
      superCallArgs.add(param.getName());
    }

    // Optional parameters (not-keyword)
    for (PyParameter param : origInfo.getOptionalParameters()) {
      newFunctionParams.add(param.getText());
    }

    // Positional vararg
    PyParameter starredParam = null;
    if (origInfo.getPositionalContainerParameter() != null) {
      starredParam = origInfo.getPositionalContainerParameter();
    }
    else if (superInfo.getPositionalContainerParameter() != null) {
      starredParam = superInfo.getPositionalContainerParameter();
    }
    else if (origInfo.getSingleStarParameter() != null) {
      starredParam = origInfo.getSingleStarParameter();
    }
    else if (superInfo.getSingleStarParameter() != null) {
      starredParam = superInfo.getSingleStarParameter();
    }
    if (starredParam != null) {
      newFunctionParams.add(starredParam.getText());
      if (superInfo.getPositionalContainerParameter() != null) {
        superCallArgs.add("*" + starredParam.getName());
      }
    }

    // Required keyword-only parameters
    for (PyParameter param : origInfo.getRequiredKeywordOnlyParameters()) {
      newFunctionParams.add(param.getText());
    }
    for (PyParameter param : superInfo.getRequiredKeywordOnlyParameters()) {
      if (!origInfo.getAllParameterNames().contains(param.getName())) {
        newFunctionParams.add(param.getText());
      }
      superCallArgs.add(param.getName() + "=" + param.getName());
    }

    // Optional keyword-only parameters
    for (PyParameter param : origInfo.getOptionalKeywordOnlyParameters()) {
      newFunctionParams.add(param.getText());
    }

    // Keyword vararg
    PyParameter doubleStarredParam = null;
    if (origInfo.getKeywordContainerParameter() != null) {
      doubleStarredParam = origInfo.getKeywordContainerParameter();
    }
    else if (superInfo.getKeywordContainerParameter() != null) {
      doubleStarredParam = superInfo.getKeywordContainerParameter();
    }
    if (doubleStarredParam != null) {
      newFunctionParams.add(doubleStarredParam.getText());
      if (superInfo.getKeywordContainerParameter() != null) {
        superCallArgs.add("**" + doubleStarredParam.getName());
      }
    }
    return Couple.of(newFunctionParams, superCallArgs);
  }

  private static class ParametersInfo {

    private final PyParameter mySelfParam;
    /**
     * Parameters without default value that come before first "*..." parameter.
     */
    private final List<PyParameter> myRequiredParams = new ArrayList<PyParameter>();
    /**
     * Parameters with default value that come before first "*..." parameter.
     */
    private final List<PyParameter> myOptionalParams = new ArrayList<PyParameter>();
    /**
     * Parameter of form "*args" (positional vararg), not the same as single "*".
     */
    private final PyParameter myPositionalContainerParam;
    /**
     * Parameter "*", that is used to delimit normal and keyword-only parameters.
     */
    private final PyParameter mySingleStarParam;
    /**
     * Parameters without default value that come after first "*..." parameter.
     */
    private final List<PyParameter> myRequiredKwOnlyParams = new ArrayList<PyParameter>();
    /**
     * Parameters with default value that come after first "*..." parameter.
     */
    private final List<PyParameter> myOptionalKwOnlyParams = new ArrayList<PyParameter>();
    /**
     * Parameter of form "**kwargs" (keyword vararg).
     */
    private final PyParameter myKeywordContainerParam;

    private final Set<String> myAllParameterNames = new LinkedHashSet<String>();

    public ParametersInfo(@NotNull PyParameterList parameterList) {
      PyParameter positionalContainer = null;
      PyParameter singleStarParam = null;
      PyParameter keywordContainer = null;
      PyParameter selfParam = null;

      for (PyParameter param : parameterList.getParameters()) {
        myAllParameterNames.addAll(collectParameterNames(param));

        if (param.isSelf()) {
          selfParam = param;
        }
        else if (param.getText().equals("*")) {
          singleStarParam = param;
        }
        else if (param.getText().startsWith("**")) {
          keywordContainer = param;
        }
        else if (param.getText().startsWith("*")) {
          positionalContainer = param;
        }
        else if (positionalContainer == null && singleStarParam == null) {
          if (param.hasDefaultValue()) {
            myOptionalParams.add(param);
          }
          else {
            myRequiredParams.add(param);
          }
        }
        else {
          if (param.hasDefaultValue()) {
            myOptionalKwOnlyParams.add(param);
          }
          else {
            myRequiredKwOnlyParams.add(param);
          }
        }
      }

      mySelfParam = selfParam;
      myPositionalContainerParam = positionalContainer;
      mySingleStarParam = singleStarParam;
      myKeywordContainerParam = keywordContainer;
    }

    @Nullable
    public PyParameter getSelfParameter() {
      return mySelfParam;
    }

    @NotNull
    public List<PyParameter> getRequiredParameters() {
      return Collections.unmodifiableList(myRequiredParams);
    }

    @NotNull
    public List<PyParameter> getOptionalParameters() {
      return Collections.unmodifiableList(myOptionalParams);
    }

    @Nullable
    public PyParameter getPositionalContainerParameter() {
      return myPositionalContainerParam;
    }

    @Nullable
    public PyParameter getSingleStarParameter() {
      return mySingleStarParam;
    }

    @NotNull
    public List<PyParameter> getRequiredKeywordOnlyParameters() {
      return Collections.unmodifiableList(myRequiredKwOnlyParams);
    }

    @NotNull
    public List<PyParameter> getOptionalKeywordOnlyParameters() {
      return Collections.unmodifiableList(myOptionalKwOnlyParams);
    }

    @Nullable
    public PyParameter getKeywordContainerParameter() {
      return myKeywordContainerParam;
    }

    @NotNull
    public Set<String> getAllParameterNames() {
      return Collections.unmodifiableSet(myAllParameterNames);
    }
  }

  @NotNull
  private static Set<String> collectParameterNames(@NotNull PyParameter param) {
    final LinkedHashSet<String> result = new LinkedHashSet<String>();
    collectParameterNames(param, result);
    return Collections.unmodifiableSet(result);
  }


  private static void collectParameterNames(@NotNull PyParameter param, @NotNull Collection<String> acc) {
    final PyTupleParameter tupleParam = param.getAsTuple();
    if (tupleParam != null) {
      for (PyParameter subParam : tupleParam.getContents()) {
        collectParameterNames(subParam, acc);
      }
    }
    else {
      ContainerUtil.addIfNotNull(acc, param.getName());
    }
  }
}
