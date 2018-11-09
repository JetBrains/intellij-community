// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementPart;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract statement part implementation; extracts the statements list.
 * User: dcheryasov
 */
public abstract class PyStatementPartImpl extends PyElementImpl implements PyStatementPart {
  protected PyStatementPartImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  @NotNull
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LIST);
  }
}
