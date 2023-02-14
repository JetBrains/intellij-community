/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.findUsages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import org.jetbrains.annotations.NotNull;


public class PyReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(@NotNull PsiElement element) {
    return element instanceof PyTargetExpression || element instanceof PyReferenceExpression;
  }

  @Override
  public boolean isDeclarationWriteAccess(@NotNull PsiElement element) {
    return element instanceof PyTargetExpression || element.getParent() instanceof PyDelStatement;
  }

  @NotNull
  @Override
  public Access getReferenceAccess(@NotNull PsiElement referencedElement, @NotNull PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @NotNull
  @Override
  public Access getExpressionAccess(@NotNull PsiElement expression) {
    if (isDeclarationWriteAccess(expression)) {
      return Access.Write;
    }
    if (expression instanceof PyReferenceExpression) {
      final PyAugAssignmentStatement statement = PyAugAssignmentStatementNavigator.getStatementByTarget(expression);
      if (statement != null) {
        return Access.ReadWrite;
      }
    }
    return Access.Read;
  }
}
