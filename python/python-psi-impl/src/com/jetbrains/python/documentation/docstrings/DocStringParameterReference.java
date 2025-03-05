// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings;

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
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class DocStringParameterReference extends PsiReferenceBase<PyStringLiteralExpression> implements PsiReferenceEx {
  private final ReferenceType myType;

  public DocStringParameterReference(PyStringLiteralExpression element, TextRange range, ReferenceType refType) {
    super(element, range);
    myType = refType;
  }

  public enum ReferenceType {PARAMETER, PARAMETER_TYPE, KEYWORD, VARIABLE, CLASS_VARIABLE, INSTANCE_VARIABLE, GLOBAL_VARIABLE}

  @Override
  public PyElement resolve() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      return resolveParameter((PyFunction)owner);
    }
    if (owner instanceof PyClass pyClass) {
      final PyFunction init = pyClass.findMethodByName(PyNames.INIT, false, null);
      if (myType == ReferenceType.PARAMETER) {
        return init != null ? resolveParameter(init) : resolveClassVariable(pyClass);
      }
      if (myType == ReferenceType.INSTANCE_VARIABLE || myType == ReferenceType.VARIABLE || myType == ReferenceType.PARAMETER_TYPE) {
        if (myType == ReferenceType.PARAMETER_TYPE && init != null) {
          PyNamedParameter parameter = resolveParameter(init);
          if (parameter != null) {
            return parameter;
          }
        }
        final PyElement instanceAttr = resolveInstanceVariable(pyClass);
        return instanceAttr != null ? instanceAttr : resolveClassVariable(pyClass);
      }
      if (myType == ReferenceType.CLASS_VARIABLE) {
        return resolveClassVariable(pyClass);
      }
    }
    if (owner instanceof PyFile && myType == ReferenceType.GLOBAL_VARIABLE) {
      return resolveGlobalVariable(((PyFile)owner));
    }
    return null;
  }

  private @Nullable PyTargetExpression resolveGlobalVariable(@NotNull PyFile owner) {
    return owner.findTopLevelAttribute(getCanonicalText());
  }

  private @Nullable PyTargetExpression resolveInstanceVariable(@NotNull PyClass owner) {
    return owner.findInstanceAttribute(getCanonicalText(), true);
  }

  private @Nullable PyTargetExpression resolveClassVariable(@NotNull PyClass owner) {
    return owner.findClassAttribute(getCanonicalText(), true, null);
  }

  private @Nullable PyNamedParameter resolveParameter(PyFunction owner) {
    final PyParameterList parameterList = owner.getParameterList();
    final PyNamedParameter resolved = parameterList.findParameterByName(getCanonicalText());
    if (resolved != null) {
      return resolved;
    }
    for (PyParameter parameter : parameterList.getParameters()) {
      if (parameter instanceof PyNamedParameter namedParameter) {
        if (namedParameter.isKeywordContainer() || namedParameter.isPositionalContainer()) {
          return namedParameter;
        }
      }
    }
    return null;
  }

  @Override
  public Object @NotNull [] getVariants() {
    // see PyDocstringCompletionContributor
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  public @NotNull List<PyNamedParameter> collectParameterVariants() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      List<PyNamedParameter> result = new ArrayList<>();
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

  @Override
  public @Nullable HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    return HighlightSeverity.WEAK_WARNING;
  }

  @Override
  public @Nullable String getUnresolvedDescription() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction function) {
      return PyPsiBundle.message("unresolved.docstring.param.reference", function.getName(), getCanonicalText());
    }
    return null;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    TextRange range = getRangeInElement();
    Pair<String, String> quotes = PyStringLiteralCoreUtil.getQuotes(range.substring(myElement.getText()));

    if (quotes != null) {
      range = TextRange.create(range.getStartOffset() + quotes.first.length(), range.getEndOffset() - quotes.second.length());
    }

    String newName = range.replace(myElement.getText(), newElementName);
    myElement.replace(PyElementGenerator.getInstance(myElement.getProject()).createStringLiteralAlreadyEscaped(newName));
    return myElement;
  }
}
