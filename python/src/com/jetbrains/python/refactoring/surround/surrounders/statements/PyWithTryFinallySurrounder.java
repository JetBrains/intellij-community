/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.refactoring.surround.surrounders.statements;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyStatementList;
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
    final PyFinallyPart finallyPart = tryStatement.getFinallyPart();
    assert finallyPart != null;
    final PyStatementList statementList = finallyPart.getStatementList();
    return statementList.getTextRange();
  }
}
