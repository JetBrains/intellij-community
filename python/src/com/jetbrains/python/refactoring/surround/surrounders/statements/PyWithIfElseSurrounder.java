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
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 27, 2009
 * Time: 8:45:08 PM
 */
public class PyWithIfElseSurrounder extends PyStatementSurrounder {
  @Override
  @Nullable
  protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
    throws IncorrectOperationException {
    PyIfStatement ifStatement =
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if True:\n    pass\nelse:    pass\n");
    final PsiElement parent = elements[0].getParent();
    final PyStatementList statementList = ifStatement.getIfPart().getStatementList();
    statementList.addRange(elements[0], elements[elements.length - 1]);
    statementList.deleteChildRange(statementList.getFirstChild(), statementList.getFirstChild());

    ifStatement = (PyIfStatement) parent.addBefore(ifStatement, elements[0]);
    parent.deleteChildRange(elements[0], elements[elements.length - 1]);

    ifStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(ifStatement);
    if (ifStatement == null) {
      return null;
    }
    return ifStatement.getTextRange();
  }

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.ifelse.template");
  }
}
