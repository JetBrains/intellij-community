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
package com.jetbrains.python.documentation.docstrings;

import com.google.common.collect.Lists;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class DocStringParameterReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx {
  private final ReferenceType myType;

  public DocStringParameterReference(PyStringLiteralExpression element, TextRange range, ReferenceType refType) {
    super(element, range);
    myType = refType;
  }

  public enum ReferenceType {PARAMETER, PARAMETER_TYPE, KEYWORD, VARIABLE, CLASS_VARIABLE, INSTANCE_VARIABLE, GLOBAL_VARIABLE}

  @Override
  public PsiElement resolve() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      return resolveParameter((PyFunction)owner);
    }
    if (owner instanceof PyClass) {
      final PyFunction init = ((PyClass)owner).findMethodByName(PyNames.INIT, false, null);
      if (init != null) {
        PsiElement element = resolveParameter(init);
        if (element == null && (myType.equals(ReferenceType.CLASS_VARIABLE) ||
                                myType.equals(ReferenceType.PARAMETER_TYPE)))
          element = resolveClassVariable((PyClass)owner);
        if (element == null && (myType.equals(ReferenceType.INSTANCE_VARIABLE) ||
                                myType.equals(ReferenceType.PARAMETER_TYPE)))
          element = resolveInstanceVariable((PyClass)owner);
        return element;
      }
      else {
        if (myType.equals(ReferenceType.CLASS_VARIABLE) ||
            myType.equals(ReferenceType.PARAMETER_TYPE))
          return resolveClassVariable((PyClass)owner);
        if (myType.equals(ReferenceType.INSTANCE_VARIABLE) ||
            myType.equals(ReferenceType.PARAMETER_TYPE))
          return resolveInstanceVariable((PyClass)owner);
      }
    }
    if (owner instanceof PyFile && myType == ReferenceType.GLOBAL_VARIABLE) {
      return resolveGlobalVariable(((PyFile)owner));
    }
    return null;
  }

  @Nullable
  private PsiElement resolveGlobalVariable(@NotNull PyFile owner) {
    for (PyTargetExpression assignment : owner.getTopLevelAttributes()) {
      if (getCanonicalText().equals(assignment.getName())) {
        return assignment;
      }
    }
    return null;
  }

  @Nullable
  private PsiElement resolveInstanceVariable(final PyClass owner) {
    final List<PyTargetExpression> attributes = owner.getInstanceAttributes();
    for (PyTargetExpression element : attributes) {
      if (getCanonicalText().equals(element.getName()))
        return element;
    }
    return null;
  }

  @Nullable
  private PsiElement resolveClassVariable(@NotNull final PyClass owner) {
    final List<PyTargetExpression> attributes = owner.getClassAttributes();
    for (PyTargetExpression element : attributes) {
      if (getCanonicalText().equals(element.getName()))
        return element;
    }
    return null;
  }

  @Nullable
  private PsiElement resolveParameter(PyFunction owner) {
    final PyParameterList parameterList = owner.getParameterList();
    final PyNamedParameter resolved = parameterList.findParameterByName(getCanonicalText());
    if (resolved != null) {
      return resolved;
    }
    for (PyParameter parameter : parameterList.getParameters()) {
      if (parameter instanceof PyNamedParameter) {
        final PyNamedParameter namedParameter = (PyNamedParameter)parameter;
        if (namedParameter.isKeywordContainer() || namedParameter.isPositionalContainer()) {
          return namedParameter;
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    // see PyDocstringCompletionContributor
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @NotNull
  public List<PyNamedParameter> collectParameterVariants() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      List<PyNamedParameter> result = Lists.newArrayList();
      final List<PyNamedParameter> namedParameters = ParamHelper.collectNamedParameters(((PyFunction)owner).getParameterList());
      Set<String> usedParameters = new HashSet<>();
      PyStringLiteralExpression expression = PsiTreeUtil.getParentOfType(getElement(), PyStringLiteralExpression.class, false);
      if (expression != null) {
        PsiReference[] references = expression.getReferences();
        for (PsiReference ref : references) {
          if (ref instanceof DocStringParameterReference && ((DocStringParameterReference)ref).getType().equals(myType))
            usedParameters.add(ref.getCanonicalText());
        }
      }
      for (PyNamedParameter param : namedParameters) {
        if (!usedParameters.contains(param.getName()))
          result.add(param);
      }

      return result;
    }
    return Collections.emptyList();
  }

  public ReferenceType getType() {
    return myType;
  }

  @Nullable
  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    return HighlightSeverity.WEAK_WARNING;
  }

  @Nullable
  @Override
  public String getUnresolvedDescription() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      PyFunction function = (PyFunction)owner;
      return "Function '" + function.getName() + "' does not have a parameter '" + getCanonicalText() + "'";
    }
    return null;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    TextRange range = getRangeInElement();
    Pair<String, String> quotes = PythonStringUtil.getQuotes(range.substring(myElement.getText()));

    if (quotes != null) {
      range = TextRange.create(range.getStartOffset() + quotes.first.length(), range.getEndOffset() - quotes.second.length());
    }

    String newName = range.replace(myElement.getText(), newElementName);
    myElement.replace(PyElementGenerator.getInstance(myElement.getProject()).createStringLiteralAlreadyEscaped(newName));
    return myElement;
  }
}
