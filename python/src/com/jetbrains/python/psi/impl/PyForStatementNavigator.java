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
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyForStatementNavigator {
  private PyForStatementNavigator() {
  }

  @Nullable
  public static PyForStatement getPyForStatementByIterable(final PsiElement element){
    final PyForStatement forStatement = PsiTreeUtil.getParentOfType(element, PyForStatement.class, false);
    if (forStatement == null){
      return null;
    }
    final PyExpression target = forStatement.getForPart().getTarget();
    if (target != null && PsiTreeUtil.isAncestor(target, element, false)){
      return forStatement;
    }
    return null;
  }

  @Nullable
  public static Object getPyForStatementByBody(final PsiElement element) {
    final PyForStatement forStatement = PsiTreeUtil.getParentOfType(element, PyForStatement.class, false);
    if (forStatement == null){
      return null;
    }
    final PyForPart forPart = forStatement.getForPart();
    return forPart == element || forPart.getStatementList() == element ? forStatement : null;
  }
}
