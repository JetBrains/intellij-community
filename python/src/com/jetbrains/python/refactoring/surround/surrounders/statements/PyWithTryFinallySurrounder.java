// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTryExceptStatement;

public class PyWithTryFinallySurrounder extends PyWithTryExceptSurrounder {
  @Override
  public String getTemplateDescription() {
    //noinspection DialogTitleCapitalization
    return CodeInsightBundle.message("surround.with.try.finally.template");
  }

  @Override
  protected String getTemplate() {
    return "try:\n    pass\nfinally:\n    pass";
  }

  @Override
  protected TextRange getResultRange(PyTryExceptStatement tryStatement) {
    final PyFinallyPart finallyPart = tryStatement.getFinallyPart();
    assert finallyPart != null;
    final PyStatementList statementList = finallyPart.getStatementList();
    return statementList.getTextRange();
  }
}
