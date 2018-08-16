// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyTryUnwrapper extends PyUnwrapper {
  public PyTryUnwrapper() {
    super(PyBundle.message("unwrap.try"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PyTryExceptStatement;
  }

  @Override
  protected void doUnwrap(final PsiElement element, final Context context) throws IncorrectOperationException {
    final PyTryExceptStatement statement = (PyTryExceptStatement)element;
    context.extractPart(statement);
    context.extractPart(statement.getElsePart());
    context.extractPart(statement.getFinallyPart());
    context.delete(statement);
  }
}

