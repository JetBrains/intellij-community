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
package com.jetbrains.python.psi.impl.references;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public class PyOperatorReference extends PyReferenceImpl {
  public PyOperatorReference(PyQualifiedExpression element, @NotNull PyResolveContext context) {
    super(element, context);
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    List<RatedResolveResult> res = new ArrayList<RatedResolveResult>();
    if (myElement instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)myElement;
      final String name = expr.getReferencedName();
      if (PyNames.CONTAINS.equals(name)) {
        res = resolveMember(expr.getRightExpression(), name);
      }
      else {
        if (PyNames.DIV.equals(name) && isTrueDivEnabled(myElement)) {
          resolveLeftAndRightOperators(res, expr, PyNames.TRUEDIV);
        }
        resolveLeftAndRightOperators(res, expr, name);
      }
    }
    else if (myElement instanceof PySubscriptionExpression) {
      final PySubscriptionExpression expr = (PySubscriptionExpression)myElement;
      res = resolveMember(expr.getOperand(), expr.getReferencedName());
    }
    else if (myElement instanceof PyPrefixExpression) {
      final PyPrefixExpression expr = (PyPrefixExpression)myElement;
      res = resolveMember(expr.getOperand(), expr.getReferencedName());
    }
    return res;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PyParameter || element instanceof PyTargetExpression) {
      return false;
    }
    return super.isReferenceTo(element);
  }

  @Override
  public String toString() {
    return "PyOperatorReference(" + myElement + "," + myContext + ")";
  }

  public String getReadableOperatorName() {
    final String name = myElement.getReferencedName();
    if (PyNames.SUBSCRIPTION_OPERATORS.contains(name)) {
      return "[]";
    }
    else {
      return getRangeInElement().substring(myElement.getText());
    }
  }

  @Nullable
  public PyExpression getReceiver() {
    if (myElement instanceof PyBinaryExpression) {
      return ((PyBinaryExpression)myElement).getLeftExpression();
    }
    else if (myElement instanceof PySubscriptionExpression) {
      return ((PySubscriptionExpression)myElement).getOperand();
    }
    else if (myElement instanceof PyPrefixExpression) {
      return ((PyPrefixExpression)myElement).getOperand();
    }
    return null;
  }

  private static String leftToRightOperatorName(String name) {
    return name.replaceFirst("__([a-z]+)__", "__r$1__");
  }

  private static boolean isTrueDivEnabled(@NotNull PyElement anchor) {
    final PsiFile file = anchor.getContainingFile();
    if (file instanceof PyFile) {
      final PyFile pyFile = (PyFile)file;
      return FutureFeature.DIVISION.requiredAt(pyFile.getLanguageLevel()) || pyFile.hasImportFromFuture(FutureFeature.DIVISION);
    }
    return false;
  }

  private void resolveLeftAndRightOperators(List<RatedResolveResult> res, PyBinaryExpression expr, String name) {
    final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
    typeEvalContext.trace("Trying to resolve left operator");
    typeEvalContext.traceIndent();
    try {
      res.addAll(resolveMember(expr.getLeftExpression(), name));
    }
    finally {
      typeEvalContext.traceUnindent();
    }
    typeEvalContext.trace("Trying to resolve right operator");
    typeEvalContext.traceIndent();
    try {
      res.addAll(resolveMember(expr.getRightExpression(), leftToRightOperatorName(name)));
    }
    finally {
      typeEvalContext.traceUnindent();
    }
  }

  @NotNull
  private List<RatedResolveResult> resolveMember(@Nullable PyExpression object, @Nullable String name) {
    final ArrayList<RatedResolveResult> results = new ArrayList<RatedResolveResult>();
    if (object != null && name != null) {
      final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
      final PyType type = typeEvalContext.getType(object);
      typeEvalContext.trace("Side text is %s, type is %s", object.getText(), type);
      if (type != null) {
        List<? extends RatedResolveResult> res = type.resolveMember(name, object, AccessDirection.of(myElement), myContext);
        if (res != null && res.size() > 0) {
          results.addAll(res);
        }
        else if (typeEvalContext.tracing()) {
          VirtualFile vFile = null;
          if (type instanceof PyClassType) {
            final PyClass pyClass = ((PyClassType)type).getPyClass();
            vFile = pyClass.getContainingFile().getVirtualFile();
          }
          type.resolveMember(name, object, AccessDirection.of(myElement), myContext);
          typeEvalContext.trace("Could not resolve member %s in type %s from file %s", name, type, vFile);
        }
      }
    }
    return results;
  }
}
