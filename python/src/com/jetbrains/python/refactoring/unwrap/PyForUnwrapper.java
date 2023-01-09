// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyForUnwrapper extends PyUnwrapper {
  public PyForUnwrapper() {
    super(PyBundle.message("unwrap.for"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    if (e instanceof PyForStatement forStatement) {
      final PyStatementList statementList = forStatement.getForPart().getStatementList();
      final PyStatement[] statements = statementList.getStatements();
      return statements.length == 1 && !(statements[0] instanceof PyPassStatement) || statements.length > 1;
    }
    return false;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyForStatement forStatement = (PyForStatement)element;
    context.extractPart(forStatement);
    context.extractPart(forStatement.getElsePart());
    context.delete(forStatement);
  }
}

