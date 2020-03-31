// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyWhilePart;
import com.jetbrains.python.psi.PyWhileStatement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyWhileStatementImpl extends PyPartitionedElementImpl implements PyWhileStatement {
  public PyWhileStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWhileStatement(this);
  }

  @Override
  @NotNull
  public PyWhilePart getWhilePart() {
    return (PyWhilePart)getPartNotNull(PyElementTypes.WHILE_PART);
  }

  @Override
  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }
}
