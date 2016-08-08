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
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
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
    final PyClass[] superClasses = klass.getSuperClasses(null);
    if (superClasses.length == 0) return;

    final PyClass superClass = superClasses[0];
    final PyFunction superInit = superClass.findMethodByName(PyNames.INIT, true, null);
    if (superInit == null) return;

    final ParametersInfo origInfo = new ParametersInfo(problemFunction.getParameterList());
    final ParametersInfo superInfo = new ParametersInfo(superInit.getParameterList());
    final boolean addSelfToCall;

    if (klass.isNewStyleClass(null)) {
      addSelfToCall = false;
      if (LanguageLevel.forElement(klass).isPy3K()) {
        superCall.append("super().__init__(");
      }
      else {
        superCall.append("super(").append(klass.getName()).append(", ").append(getSelfParameterName(origInfo)).append(").__init__(");
      }
    }
    else {
      addSelfToCall = true;
      superCall.append(superClass.getName()).append(".__init__(");
    }

    final Couple<List<String>> couple = buildNewFunctionParamsAndSuperInitCallArgs(origInfo, superInfo, addSelfToCall);
    final StringBuilder newParameters = new StringBuilder("(");
    StringUtil.join(couple.getFirst(), ", ", newParameters);
    newParameters.append(")");

    StringUtil.join(couple.getSecond(), ", ", superCall);
    superCall.append(")");

    final PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final LanguageLevel languageLevel = LanguageLevel.forElement(problemFunction);
    final PyStatement callSuperStatement = generator.createFromText(languageLevel, PyStatement.class, superCall.toString());
    final PyParameterList newParameterList = generator.createParameterList(languageLevel, newParameters.toString());
    problemFunction.getParameterList().replace(newParameterList);
    final PyStatementList statementList = problemFunction.getStatementList();
    PyUtil.addElementToStatementList(callSuperStatement, statementList, true);
    PyPsiUtils.removeRedundantPass(statementList);
  }

  @NotNull
  private static String getSelfParameterName(@NotNull ParametersInfo info) {
    final PyParameter selfParameter = info.getSelfParameter();
    if (selfParameter == null) {
      return PyNames.CANONICAL_SELF;
    }
    return StringUtil.defaultIfEmpty(selfParameter.getName(), PyNames.CANONICAL_SELF);
  }

  @NotNull
  private static Couple<List<String>> buildNewFunctionParamsAndSuperInitCallArgs(@NotNull ParametersInfo origInfo,
                                                                                 @NotNull ParametersInfo superInfo,
                                                                                 boolean addSelfToCall) {
    final List<String> newFunctionParams = new ArrayList<>();
    final List<String> superCallArgs = new ArrayList<>();

    final PyParameter selfParameter = origInfo.getSelfParameter();
    if (selfParameter != null && StringUtil.isNotEmpty(selfParameter.getName())) {
      newFunctionParams.add(selfParameter.getText());
    }
    else {
      newFunctionParams.add(PyNames.CANONICAL_SELF);
    }

    if (addSelfToCall) {
      superCallArgs.add(getSelfParameterName(origInfo));
    }

    // Required parameters (not-keyword)
    for (PyParameter param : origInfo.getRequiredParameters()) {
      newFunctionParams.add(param.getText());
    }
    for (PyParameter param : superInfo.getRequiredParameters()) {
      // Special case as if base class has constructor __init__((a, b), c) and
      // subclass has constructor __init__(a, (b, c))
      final PyTupleParameter tupleParam = param.getAsTuple();
      if (tupleParam != null) {
        final List<String> uniqueNames = collectParameterNames(tupleParam);
        final boolean hasDuplicates = uniqueNames.removeAll(origInfo.getAllParameterNames());
        if (hasDuplicates) {
          newFunctionParams.addAll(uniqueNames);
        }
        else {
          newFunctionParams.add(param.getText());
        }
        // Retain original structure of tuple parameter.
        // Note that tuple parameters cannot have annotations or nested default values, so it's syntactically safe
        superCallArgs.add(param.getText());
      }
      else {
        if (!origInfo.getAllParameterNames().contains(param.getName())) {
          newFunctionParams.add(param.getText());
        }
        superCallArgs.add(param.getName());
      }
    }

    // Optional parameters (not-keyword)
    for (PyParameter param : origInfo.getOptionalParameters()) {
      newFunctionParams.add(param.getText());
    }

    // Pass parameters with default values to super class constructor, only if both functions contain them  
    for (PyParameter param : superInfo.getOptionalParameters()) {
      final PyTupleParameter tupleParam = param.getAsTuple();
      if (tupleParam != null) {
        if (origInfo.getAllParameterNames().containsAll(collectParameterNames(tupleParam))) {
          final String paramText = tupleParam.getText();
          final PsiElement equalSign = PyPsiUtils.getFirstChildOfType(param, PyTokenTypes.EQ);
          if (equalSign != null) {
            superCallArgs.add(paramText.substring(0, equalSign.getStartOffsetInParent()).trim());
          }
        }
      }
      else {
        if (origInfo.getAllParameterNames().contains(param.getName())) {
          superCallArgs.add(param.getName());
        }
      }
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
    boolean newSignatureContainsKeywordParams = false;
    for (PyParameter param : origInfo.getRequiredKeywordOnlyParameters()) {
      newFunctionParams.add(param.getText());
      newSignatureContainsKeywordParams = true;
    }
    for (PyParameter param : superInfo.getRequiredKeywordOnlyParameters()) {
      if (!origInfo.getAllParameterNames().contains(param.getName())) {
        newFunctionParams.add(param.getText());
        newSignatureContainsKeywordParams = true;
      }
      superCallArgs.add(param.getName() + "=" + param.getName());
    }

    // Optional keyword-only parameters
    for (PyParameter param : origInfo.getOptionalKeywordOnlyParameters()) {
      newFunctionParams.add(param.getText());
      newSignatureContainsKeywordParams = true;
    }
    
    // If '*' param is followed by nothing in result signature, remove it altogether 
    if (starredParam instanceof PySingleStarParameter && !newSignatureContainsKeywordParams) {
      newFunctionParams.remove(newFunctionParams.size() - 1);
    }

    for (PyParameter param : superInfo.getOptionalKeywordOnlyParameters()) {
      if (origInfo.getAllParameterNames().contains(param.getName())) {
        superCallArgs.add(param.getName() + "=" + param.getName());
      }
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
    private final List<PyParameter> myRequiredParams = new ArrayList<>();
    /**
     * Parameters with default value that come before first "*..." parameter.
     */
    private final List<PyParameter> myOptionalParams = new ArrayList<>();
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
    private final List<PyParameter> myRequiredKwOnlyParams = new ArrayList<>();
    /**
     * Parameters with default value that come after first "*..." parameter.
     */
    private final List<PyParameter> myOptionalKwOnlyParams = new ArrayList<>();
    /**
     * Parameter of form "**kwargs" (keyword vararg).
     */
    private final PyParameter myKeywordContainerParam;

    private final Set<String> myAllParameterNames = new LinkedHashSet<>();

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
        else if (param instanceof PySingleStarParameter) {
          singleStarParam = param;
        }
        else if (param.getAsNamed() != null && param.getAsNamed().isKeywordContainer()) {
          keywordContainer = param;
        }
        else if (param.getAsNamed() != null && param.getAsNamed().isPositionalContainer()) {
          positionalContainer = param;
        }
        else if (param.getAsNamed() == null || !param.getAsNamed().isKeywordOnly()) {
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
  private static List<String> collectParameterNames(@NotNull PyParameter param) {
    final List<String> result = new ArrayList<>();
    collectParameterNames(param, result);
    return result;
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
