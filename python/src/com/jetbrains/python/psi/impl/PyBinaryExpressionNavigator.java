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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyBinaryExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyBinaryExpressionNavigator {
  private PyBinaryExpressionNavigator() {
  }

  @Nullable
  public static PyBinaryExpression getBinaryExpressionByOperand(final PsiElement element) {
    final PyBinaryExpression expression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    if (expression == null){
      return null;
    }
    if (expression.getPsiOperator() == element){
      return expression;
    }
    return null;
  }
}
