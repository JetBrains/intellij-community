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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import org.jetbrains.annotations.Nullable;

public final class PyListCompExpressionNavigator {
  private PyListCompExpressionNavigator() {
  }

  @Nullable
  public static PyListCompExpression getPyListCompExpressionByVariable(final PsiElement element){
    final PyListCompExpression listCompExpression = PsiTreeUtil.getParentOfType(element, PyListCompExpression.class, false);
    if (listCompExpression == null){
      return null;
    }
    for (PyComprehensionForComponent component : listCompExpression.getForComponents()) {
      final PyExpression variable = component.getIteratorVariable();
      if (variable != null && PsiTreeUtil.isAncestor(variable, element, false)){
        return listCompExpression;
      }
    }
    return null;
  }
}
