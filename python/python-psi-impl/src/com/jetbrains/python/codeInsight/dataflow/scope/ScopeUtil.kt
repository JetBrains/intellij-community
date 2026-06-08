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
package com.jetbrains.python.codeInsight.dataflow.scope

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache.getControlFlow
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache.getScope
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.impl.PyExceptPartNavigator
import com.jetbrains.python.psi.impl.PyForStatementNavigator
import com.jetbrains.python.psi.impl.PyListCompExpressionNavigator

object ScopeUtil {
  @JvmStatic
  fun getParameterScope(element: PsiElement?): PsiElement? {
    if (element is PyNamedParameter) {
      val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
      if (function != null) {
        return function
      }
    }

    val exceptPart = PyExceptPartNavigator.getPyExceptPartByTarget(element)
    if (exceptPart != null) {
      return exceptPart
    }

    val forStatement = PyForStatementNavigator.getPyForStatementByIterable(element)
    if (forStatement != null) {
      return forStatement
    }

    val listCompExpression = PyListCompExpressionNavigator.getPyListCompExpressionByVariable(element)
    if (listCompExpression != null) {
      return listCompExpression
    }
    return null
  }

  /**
   * Return the scope owner for the element. This also applies for elements of instance `AstScopeOwner`.
   * <br></br>
   * Scope owner is not always the first ScopeOwner parent of the element. Some elements are resolved in outer scopes.
   * <br></br>
   * This method does not access AST if underlying PSI is stub based.
   */
  @JvmStatic
  fun getScopeOwner(element: PsiElement?): ScopeOwner? {
    return ScopeUtilCore.getScopeOwner(element) as ScopeOwner?
  }

  @JvmStatic
  fun getDeclarationScopeOwner(anchor: PsiElement?, name: String?): ScopeOwner? {
    if (name != null) {
      val originalScopeOwner = getScopeOwner(anchor)
      var scopeOwner = originalScopeOwner
      while (scopeOwner != null) {
        if (scopeOwner !is PyClass || scopeOwner === originalScopeOwner) {
          val scope = getScope(scopeOwner)
          if (scope.containsDeclaration(name)) {
            return scopeOwner
          }
        }
        scopeOwner = getScopeOwner(scopeOwner)
      }
    }
    return null
  }

  @JvmStatic
  fun getElementsOfAccessType(
    name: String,
    scopeOwner: ScopeOwner,
    type: ReadWriteInstruction.ACCESS,
  ): List<PsiElement> =
    getControlFlow(scopeOwner).instructions
      .filterIsInstance<ReadWriteInstruction>()
      .filter { name == it.name && type == it.access }
      .mapNotNull { it.element }
}
