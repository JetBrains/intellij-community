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

public final class ScopeUtil {
  private ScopeUtil() {
  }

  public static @Nullable PsiElement getParameterScope(final PsiElement element){
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
  public static @Nullable ScopeOwner getScopeOwner(final @Nullable PsiElement element) {
    return (ScopeOwner)ScopeUtilCore.getScopeOwner(element);
  }

  public static @Nullable ScopeOwner getDeclarationScopeOwner(@Nullable PsiElement anchor, @Nullable String name) {
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

  public static @NotNull List<PsiElement> getElementsOfAccessType(@NotNull String name,
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
