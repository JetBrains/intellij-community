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
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyAugAssignmentStatementImpl extends PyElementImpl implements PyAugAssignmentStatement {

  public PyAugAssignmentStatementImpl(@NotNull ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(@NotNull PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAugAssignmentStatement(this);
  }

  @Override
  @NotNull
  public PyExpression getTarget() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Override
  @Nullable
  public PyExpression getValue() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
  }

  @Override
  @Nullable
  public PsiElement getOperation() {
    return getPsiOperator();
  }

  @Nullable
  @Override
  public PyElementType getOperator() {
    final PsiElement psiOperator = getPsiOperator();
    return psiOperator != null ? (PyElementType)psiOperator.getNode().getElementType() : null;
  }

  @Nullable
  @Override
  public PsiElement getPsiOperator() {
    return PyPsiUtils.getChildByFilter(this, PyTokenTypes.AUG_ASSIGN_OPERATIONS, 0);
  }
}
