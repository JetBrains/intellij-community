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

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static com.intellij.psi.util.PsiTreeUtil.isAncestor;

/**
 * @author oleg
 */
public class ScopeUtil {
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
    if (element instanceof StubBasedPsiElement) {
      final StubElement stub = ((StubBasedPsiElement)element).getStub();
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
    // Function return annotations are resolved outside of the function
    if (firstOwner instanceof PyFunction) {
      final PyFunction function = (PyFunction)firstOwner;
      final PyAnnotation annotation = function.getAnnotation();
      if (isAncestor(annotation, element, false)) {
        return nextOwner;
      }
    }
    return firstOwner;
  }

  @Nullable
  public static ScopeOwner getDeclarationScopeOwner(PsiElement anchor, String name) {
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
  public static Collection<PsiElement> getReadWriteElements(@NotNull String name, @NotNull ScopeOwner scopeOwner, boolean isReadAccess,
                                                            boolean isWriteAccess) {
    ControlFlow flow = ControlFlowCache.getControlFlow(scopeOwner);
    Collection<PsiElement> result = new ArrayList<>();
    for (Instruction instr : flow.getInstructions()) {
      if (instr instanceof ReadWriteInstruction) {
        ReadWriteInstruction rw = (ReadWriteInstruction)instr;
        if (name.equals(rw.getName())) {
          ReadWriteInstruction.ACCESS access = rw.getAccess();
          if ((isReadAccess && access.isReadAccess()) || (isWriteAccess && access.isWriteAccess())) {
            result.add(rw.getElement());
          }
        }
      }
    }
    return result;
  }

  /*  This function compensates for the fact that scope children do not always match the AST children. For example, the decorator list is the
      AST child of a function but is not in function scope. So AST children that are not scope children must be excluded.
   */
  public static void visitChildrenInScope(ScopeOwner owner, PyElementVisitor visitor) {
    PyClass pyClass = PyUtil.as(owner, PyClass.class);
    if (pyClass != null) {
      pyClass.getStatementList().accept(visitor);
      return;
    }

    PyFunction pyFunction = PyUtil.as(owner, PyFunction.class);
    if (pyFunction != null) {
      pyFunction.getParameterList().accept(visitor);
      pyFunction.getStatementList().accept(visitor);
      return;
    }

    owner.acceptChildren(visitor);
  }
}
