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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyBreakStatementImpl extends PyElementImpl implements PyBreakStatement {
  public PyBreakStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBreakStatement(this);
  }

  @Nullable
  public PyLoopStatement getLoopStatement() {
    return getLoopStatement(this);
  }

  @Nullable
  private static PyLoopStatement getLoopStatement(@NotNull PsiElement element) {
    final PyLoopStatement loop = PsiTreeUtil.getParentOfType(element, PyLoopStatement.class);
    if (loop instanceof PyStatementWithElse) {
      final PyStatementWithElse stmt = (PyStatementWithElse)loop;
      final PyElsePart elsePart = stmt.getElsePart();
      if (PsiTreeUtil.isAncestor(elsePart, element, true)) {
        return getLoopStatement(loop);
      }
    }
    return loop;
  }
}
