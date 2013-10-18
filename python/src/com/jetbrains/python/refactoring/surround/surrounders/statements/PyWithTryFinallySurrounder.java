package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.psi.PyTryExceptStatement;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 6:52:06 PM
 */
public class PyWithTryFinallySurrounder extends PyWithTryExceptSurrounder {
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.try.finally.template");
  }

  @Override
  protected String getTemplate() {
    return "try:\n    pass\nfinally:\n    pass";
  }

  @Override
  protected TextRange getResultRange(PyTryExceptStatement tryStatement) {
    return tryStatement.getFinallyPart().getStatementList().getTextRange();
  }
}
