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
package com.jetbrains.python.documentation;

import com.google.common.collect.Lists;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class DocStringParameterReference extends PsiReferenceBase<PsiElement> implements PsiReferenceEx {
  private final String myType;

  public DocStringParameterReference(PsiElement element, TextRange range, String refType) {
    super(element, range);
    myType = refType;
  }

  @Override
  public PsiElement resolve() {
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      return resolveParameter((PyFunction)owner);
    }
    if (owner instanceof PyClass) {
      final PyFunction init = ((PyClass)owner).findMethodByName(PyNames.INIT, false);
      if (init != null) {
        PsiElement element = resolveParameter(init);
        if (element == null)
          element = resolveClassVariable(owner);
        return element;
      }
      else {
        return resolveClassVariable(owner);
      }
    }
    return null;
  }

  @Nullable
  private PsiElement resolveClassVariable(final PyDocStringOwner owner) {
    final PyStatementList statementList = ((PyClass)owner).getStatementList();
    for (PsiElement element : statementList.getChildren()) {
      if (element instanceof PyAssignmentStatement) {
        final PyExpression[] targets = ((PyAssignmentStatement)element).getTargets();
        if (targets.length > 0 && targets[0].getText().equals(getCanonicalText()))
          return targets[0];
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
    PyDocStringOwner owner = PsiTreeUtil.getParentOfType(getElement(), PyDocStringOwner.class);
    if (owner instanceof PyFunction) {
      List <PyNamedParameter> result = Lists.newArrayList();
      final List<PyNamedParameter> namedParameters = ParamHelper.collectNamedParameters(((PyFunction)owner).getParameterList());
      Set<String> usedParameters = new HashSet<String>();
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

      return ArrayUtil.toObjectArray(result);
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
  
  public String getType() {
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
}
