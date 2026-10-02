// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.impl.PyElementImpl;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public class PyTypeDeclarationStatementImpl extends PyElementImpl implements PyTypeDeclarationStatement {
  public PyTypeDeclarationStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTypeDeclarationStatement(this);
  }
}
