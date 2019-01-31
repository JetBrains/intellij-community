// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyTryExceptStatementImpl extends PyPartitionedElementImpl implements PyTryExceptStatement {
  private static final TokenSet EXCEPT_BLOCKS = TokenSet.create(PyElementTypes.EXCEPT_PART);

  public PyTryExceptStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTryExceptStatement(this);
  }

  @Override
  @NotNull
  public PyExceptPart[] getExceptParts() {
    return childrenToPsi(EXCEPT_BLOCKS, PyExceptPart.EMPTY_ARRAY);
  }

  @Override
  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @Override
  @NotNull
  public PyTryPart getTryPart() {
    return (PyTryPart)getPartNotNull(PyElementTypes.TRY_PART);
  }


  @Override
  public PyFinallyPart getFinallyPart() {
    return (PyFinallyPart)getPart(PyElementTypes.FINALLY_PART);
  }
}
