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
package com.jetbrains.python.codeInsight.dataflow.scope;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyExceptPartNavigator;
import com.jetbrains.python.psi.impl.PyForStatementNavigator;
import com.jetbrains.python.psi.impl.PyListCompExpressionNavigator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static com.intellij.psi.util.PsiTreeUtil.isAncestor;

public final class ScopeUtil {
  private ScopeUtil() {
  }

  @Nullable
  public static PsiElement getParameterScope(final PsiElement element){
    if (element instanceof PyNamedParameter){
      final PyFunction function = getParentOfType(element, PyFunction.class, false);
      if (function != null){
        return function;
      }
    }

    final PyExceptPart exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(element);
    if (exceptPart != null){
      return exceptPart;
    }

    final PyForStatement forStatement = PyForStatementNavigator.getPyForStatementByIterable(element);
    if (forStatement != null){
      return forStatement;
    }

    final PyListCompExpression listCompExpression = PyListCompExpressionNavigator.getPyListCompExpressionByVariable(element);
    if (listCompExpression != null){
      return listCompExpression;
    }
    return null;
  }

  /**
   * Return the scope owner for the element.
   *
   * Scope owner is not always the first ScopeOwner parent of the element. Some elements are resolved in outer scopes.
   *
   * This method does not access AST if underlying PSI is stub based.
   */
  @Nullable
  public static ScopeOwner getScopeOwner(@Nullable final PsiElement element) {
    if (element == null) {
      return null;
    }
    if (element instanceof PyExpressionCodeFragment) {
      final PsiElement context = element.getContext();
      return context instanceof ScopeOwner ? (ScopeOwner)context : getScopeOwner(context);
    }
    if (element instanceof StubBasedPsiElement) {
      final StubElement stub = ((StubBasedPsiElement<?>)element).getStub();
      if (stub != null) {
        StubElement parentStub = stub.getParentStub();
        while (parentStub != null) {
          final PsiElement parent = parentStub.getPsi();
          if (parent instanceof ScopeOwner) {
            return (ScopeOwner)parent;
          }
          parentStub = parentStub.getParentStub();
        }
        return null;
      }
    }
    return CachedValuesManager.getCachedValue(element, () -> CachedValueProvider.Result
      .create(calculateScopeOwnerByAST(element), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private static ScopeOwner calculateScopeOwnerByAST(@Nullable PsiElement element) {
    final ScopeOwner firstOwner = getParentOfType(element, ScopeOwner.class);
    if (firstOwner == null) {
      return null;
    }
    final ScopeOwner nextOwner = getParentOfType(firstOwner, ScopeOwner.class);
    // References in decorator expressions are resolved outside of the function (if the lambda is not inside the decorator)
    final PyElement decoratorAncestor = getParentOfType(element, PyDecorator.class);
    if (decoratorAncestor != null && !isAncestor(decoratorAncestor, firstOwner, true)) {
      return nextOwner;
    }
    // References in default values or in annotations of parameters are resolved outside of the function (if the lambda is not inside the
    // default value)
    final PyNamedParameter parameterAncestor = getParentOfType(element, PyNamedParameter.class);
    if (parameterAncestor != null && !isAncestor(parameterAncestor, firstOwner, true)) {
      final PyExpression defaultValue = parameterAncestor.getDefaultValue();
      final PyAnnotation annotation = parameterAncestor.getAnnotation();
      if (isAncestor(defaultValue, element, false) || isAncestor(annotation, element, false)) {
        return nextOwner;
      }
    }
    // Superclasses are resolved outside of the class
    final PyClass containingClass = getParentOfType(element, PyClass.class);
    if (containingClass != null && isAncestor(containingClass.getSuperClassExpressionList(), element, false)) {
      return nextOwner;
    }
    // Function return annotations and type comments are resolved outside of the function
    if (firstOwner instanceof PyFunction function) {
      if (isAncestor(function.getAnnotation(), element, false) || isAncestor(function.getTypeComment(), element, false)) {
        return nextOwner;
      }
    }
    return firstOwner;
  }

  @Nullable
  public static ScopeOwner getDeclarationScopeOwner(@Nullable PsiElement anchor, @Nullable String name) {
    if (name != null) {
      final ScopeOwner originalScopeOwner = getScopeOwner(anchor);
      ScopeOwner scopeOwner = originalScopeOwner;
      while (scopeOwner != null) {
        if (!(scopeOwner instanceof PyClass) || scopeOwner == originalScopeOwner) {
          Scope scope = ControlFlowCache.getScope(scopeOwner);
          if (scope.containsDeclaration(name)) {
            return scopeOwner;
          }
        }
        scopeOwner = getScopeOwner(scopeOwner);
      }
    }
    return null;
  }

  @NotNull
  public static List<PsiElement> getElementsOfAccessType(@NotNull String name,
                                                         @NotNull ScopeOwner scopeOwner,
                                                         @NotNull ReadWriteInstruction.ACCESS type) {
    return StreamEx
      .of(ControlFlowCache.getControlFlow(scopeOwner).getInstructions())
      .select(ReadWriteInstruction.class)
      .filter(i -> name.equals(i.getName()) && type == i.getAccess())
      .map(ReadWriteInstruction::getElement)
      .nonNull()
      .toImmutableList();
  }
}
