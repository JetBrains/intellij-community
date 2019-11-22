// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyIfStatementImpl extends PyPartitionedElementImpl implements PyIfStatement {
  public PyIfStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyIfStatement(this);
  }

  @Override
  @NotNull
  public PyIfPart getIfPart() {
    return (PyIfPart)getPartNotNull(PyElementTypes.IF_PART_IF);
  }

  @Override
  @NotNull
  public PyIfPart[] getElifParts() {
    return childrenToPsi(PyElementTypes.ELIFS, PyIfPart.EMPTY_ARRAY);
  }

  @Override
  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }
}
