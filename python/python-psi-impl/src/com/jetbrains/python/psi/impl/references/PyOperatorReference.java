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
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import kotlin.Unit;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PyOperatorReference extends PyReferenceImpl {
  public PyOperatorReference(PyQualifiedExpression element, @NotNull PyResolveContext context) {
    super(element, context);
  }

  @Override
  protected @NotNull List<RatedResolveResult> resolveInner() {
    if (myElement instanceof PyAugAssignmentStatement stmt) {
      return resolveInlineAndLeftAndRightOperators(stmt, stmt.getReferencedName());
    }
    else if (myElement instanceof PyBinaryExpression expr) {
      final String name = expr.getReferencedName();
      if (PyNames.CONTAINS.equals(name)) {
        return resolveMember(expr.getRightExpression(), name);
      }
      else {
        return resolveLeftAndRightOperators(expr, name);
      }
    }
    else if (myElement instanceof PySubscriptionExpression expr) {
      return resolveMember(expr.getOperand(), expr.getReferencedName());
    }
    else if (myElement instanceof PyPrefixExpression expr) {
      return resolveMember(expr.getOperand(), expr.getReferencedName());
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
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
    if (name != null && PyNames.SUBSCRIPTION_OPERATORS.contains(name)) {
      return "[]";
    }
    else {
      return getRangeInElement().substring(myElement.getText());
    }
  }

  public @Nullable PyExpression getReceiver() {
    if (myElement instanceof PyCallSiteExpression) {
      return ((PyCallSiteExpression)myElement).getReceiver(null);
    }
    return null;
  }

  private @NotNull List<RatedResolveResult> resolveLeftAndRightOperators(@NotNull PyBinaryExpression expr, @Nullable String name) {
    final List<RatedResolveResult> result = new ArrayList<>();

    final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
    typeEvalContext.traceWithIndent("Trying to resolve left operator", () -> {
      result.addAll(resolveMember(expr.getReceiver(null), name));
      return Unit.INSTANCE;
    });

    // A user-defined metaclass override of the operator never lets Python fall back to the
    // reflected operator on the right; skipping it here keeps inherited typeshed signatures
    // out of the inferred type. Instance-receiver paths still need both candidates (e.g. for
    // stub-defined numpy operators).
    if (isMetaclassDispatch(expr.getReceiver(null)) && hasUserDefinedResolution(result)) {
      return result;
    }

    typeEvalContext.traceWithIndent("Trying to resolve right operator", () -> {
      result.addAll(resolveMember(expr.getRightExpression(), PyNames.leftToRightOperatorName(name)));
      return Unit.INSTANCE;
    });
    return result;
  }

  private @NotNull List<RatedResolveResult> resolveInlineAndLeftAndRightOperators(@NotNull PyAugAssignmentStatement stmt, @Nullable String name) {
    final List<RatedResolveResult> result = new ArrayList<>();

    final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
    typeEvalContext.traceWithIndent("Trying to resolve inplace operator", () -> {
      result.addAll(resolveMember(stmt.getReceiver(null), name));
      return Unit.INSTANCE;
    });
    typeEvalContext.traceWithIndent("Trying to resolve left operator", () -> {
      result.addAll(resolveMember(stmt.getReceiver(null), PyNames.inplaceToLeftOperatorName(name)));
      return Unit.INSTANCE;
    });
    typeEvalContext.traceWithIndent("Trying to resolve right operator", () -> {
      result.addAll(resolveMember(stmt.getValue(), PyNames.inplaceToRightOperatorName(name)));
      return Unit.INSTANCE;
    });
    return result;
  }

  private static boolean hasUserDefinedResolution(@NotNull List<? extends RatedResolveResult> results) {
    return StreamEx.of(results)
      .map(res -> res.getElement())
      .nonNull()
      .anyMatch(element -> !PyBuiltinCache.getInstance(element).isBuiltin(element));
  }

  private boolean isMetaclassDispatch(@Nullable PyExpression receiver) {
    if (receiver == null) return false;
    final PyType type = myContext.getTypeEvalContext().getType(receiver);
    return type instanceof PyClassLikeType classLikeType && classLikeType.isDefinition();
  }

  private @NotNull List<RatedResolveResult> resolveMember(@Nullable PyExpression object, @Nullable String name) {
    final ArrayList<RatedResolveResult> results = new ArrayList<>();
    if (object != null && name != null) {
      final TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
      final PyType type = typeEvalContext.getType(object);
      typeEvalContext.trace("Side text is %s, type is %s", object.getText(), type);
      if (type != null) {
        final List<? extends RatedResolveResult> res =
          PyTypeUtil
            .toStream(type)
            .nonNull()
            .flatCollection(
              it -> it instanceof PyClassLikeType && ((PyClassLikeType)it).isDefinition()
                    ? resolveDefinitionMember((PyClassLikeType)it, object, name)
                    : it.resolveMember(name, object, AccessDirection.of(myElement), myContext)
            )
            .toList();

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

  private @Nullable List<? extends RatedResolveResult> resolveDefinitionMember(@NotNull PyClassLikeType classLikeType,
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
