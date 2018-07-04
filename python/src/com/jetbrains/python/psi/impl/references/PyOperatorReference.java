/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
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
    if (myElement instanceof PyBinaryExpression) {
      final PyBinaryExpression expr = (PyBinaryExpression)myElement;
      final String name = expr.getReferencedName();
      if (PyNames.CONTAINS.equals(name)) {
        return resolveMember(expr.getRightExpression(), name);
      }
      else {
        return resolveLeftAndRightOperators(expr, name);
      }
    }
    else if (myElement instanceof PySubscriptionExpression) {
      final PySubscriptionExpression expr = (PySubscriptionExpression)myElement;
      return resolveMember(expr.getOperand(), expr.getReferencedName());
    }
    else if (myElement instanceof PyPrefixExpression) {
      final PyPrefixExpression expr = (PyPrefixExpression)myElement;
      return resolveMember(expr.getOperand(), expr.getReferencedName());
    }
    return Collections.emptyList();
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
    if (myElement instanceof PyCallSiteExpression) {
      return ((PyCallSiteExpression)myElement).getReceiver(null);
    }
    return null;
  }

  @NotNull
  private List<RatedResolveResult> resolveLeftAndRightOperators(@NotNull PyBinaryExpression expr, @Nullable String name) {
    final List<RatedResolveResult> result = new ArrayList<>();

    final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
    typeEvalContext.trace("Trying to resolve left operator");
    typeEvalContext.traceIndent();
    try {
      result.addAll(resolveMember(expr.getReceiver(null), name));
    }
    finally {
      typeEvalContext.traceUnindent();
    }
    typeEvalContext.trace("Trying to resolve right operator");
    typeEvalContext.traceIndent();
    try {
      result.addAll(resolveMember(expr.getRightExpression(), PyNames.leftToRightOperatorName(name)));
    }
    finally {
      typeEvalContext.traceUnindent();
    }

    return result;
  }

  @NotNull
  private List<RatedResolveResult> resolveMember(@Nullable PyExpression object, @Nullable String name) {
    final ArrayList<RatedResolveResult> results = new ArrayList<>();
    if (object != null && name != null) {
      final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
      final PyType type = typeEvalContext.getType(object);
      typeEvalContext.trace("Side text is %s, type is %s", object.getText(), type);
      if (type != null) {
        final List<? extends RatedResolveResult> res =
          type instanceof PyClassLikeType && ((PyClassLikeType)type).isDefinition()
          ? resolveDefinitionMember((PyClassLikeType)type, object, name)
          : type.resolveMember(name, object, AccessDirection.of(myElement), myContext);

        if (!ContainerUtil.isEmpty(res)) {
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

  @Nullable
  private List<? extends RatedResolveResult> resolveDefinitionMember(@NotNull PyClassLikeType classLikeType,
                                                                     @NotNull PyExpression object,
                                                                     @NotNull String name) {
    final PyClassLikeType metaClassType = classLikeType.getMetaClassType(myContext.getTypeEvalContext(), true);
    if (metaClassType != null) {
      final List<? extends RatedResolveResult> results =
        metaClassType.resolveMember(name, object, AccessDirection.of(myElement), myContext);

      if (!ContainerUtil.isEmpty(results)) return results;
    }

    return name.equals(PyNames.GETITEM) && LanguageLevel.forElement(object).isAtLeast(LanguageLevel.PYTHON37)
           ? classLikeType.resolveMember(PyNames.CLASS_GETITEM, object, AccessDirection.of(myElement), myContext)
           : null;
  }
}
