// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings;

import com.google.common.collect.Lists;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
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
        if (element == null && (myType.equals(ReferenceType.CLASS_VARIABLE) || myType.equals(ReferenceType.PARAMETER_TYPE))) {
          element = resolveClassVariable((PyClass)owner);
        }
        if (element == null && (myType.equals(ReferenceType.INSTANCE_VARIABLE) || myType.equals(ReferenceType.PARAMETER_TYPE))) {
          element = resolveInstanceVariable((PyClass)owner);
        }
        return element;
      }
      else {
        PsiElement element = null;
        if (myType.equals(ReferenceType.CLASS_VARIABLE) || myType.equals(ReferenceType.PARAMETER_TYPE)) {
          element = resolveClassVariable((PyClass)owner);
        }
        if (element == null && (myType.equals(ReferenceType.INSTANCE_VARIABLE) || myType.equals(ReferenceType.PARAMETER_TYPE))) {
          element = resolveInstanceVariable((PyClass)owner);
        }
        return element;
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
      if (getCanonicalText().equals(element.getName())) {
        return element;
      }
    }
    return null;
  }

  @Nullable
  private PsiElement resolveClassVariable(@NotNull final PyClass owner) {
    final List<PyTargetExpression> attributes = owner.getClassAttributes();
    for (PyTargetExpression element : attributes) {
      if (getCanonicalText().equals(element.getName())) {
        return element;
      }
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
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
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
          if (ref instanceof DocStringParameterReference && ((DocStringParameterReference)ref).getType().equals(myType)) {
            usedParameters.add(ref.getCanonicalText());
          }
        }
      }
      for (PyNamedParameter param : namedParameters) {
        if (!usedParameters.contains(param.getName())) {
          result.add(param);
        }
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
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    TextRange range = getRangeInElement();
    Pair<String, String> quotes = PyStringLiteralUtil.getQuotes(range.substring(myElement.getText()));

    if (quotes != null) {
      range = TextRange.create(range.getStartOffset() + quotes.first.length(), range.getEndOffset() - quotes.second.length());
    }

    String newName = range.replace(myElement.getText(), newElementName);
    myElement.replace(PyElementGenerator.getInstance(myElement.getProject()).createStringLiteralAlreadyEscaped(newName));
    return myElement;
  }
}
