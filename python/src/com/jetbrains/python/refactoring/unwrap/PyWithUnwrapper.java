// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.unwrap;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyWithStatement;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyWithUnwrapper extends PyUnwrapper {
  public PyWithUnwrapper() {
    super(PyBundle.message("unwrap.with"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    if (e instanceof PyWithStatement) {
      ASTNode n = e.getNode().findChildByType(PyElementTypes.STATEMENT_LISTS);
      if (n != null) {
        final PyStatementList statementList = (PyStatementList)n.getPsi();
        if (statementList != null) {
          final PyStatement[] statements = statementList.getStatements();
          return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
        }
      }
    }
    return false;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyWithStatement withStatement = (PyWithStatement)element;
    context.extractPart(withStatement);
    context.delete(withStatement);
  }
}

