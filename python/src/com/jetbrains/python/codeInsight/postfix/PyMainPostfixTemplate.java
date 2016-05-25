/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.postfix;

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.refactoring.surround.surrounders.statements.PyStatementSurrounder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyMainPostfixTemplate extends SurroundPostfixTemplateBase {

  public static final String DESCR = "if __name__ == '__main__': expr";

  protected PyMainPostfixTemplate() {
    super("main", DESCR, PyPostfixUtils.PY_PSI_INFO, PyPostfixUtils.currentStatementSelector());
  }

  @NotNull
  @Override
  protected Surrounder getSurrounder() {
    return new PyStatementSurrounder() {
      @Nullable
      @Override
      protected TextRange surroundStatement(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement[] elements)
        throws IncorrectOperationException {
        PyIfStatement ifStatement = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.forElement(elements[0]), PyIfStatement.class, "if __name__ == '__main__':\n expr");
        ifStatement = (PyIfStatement)CodeStyleManager.getInstance(project).reformat(ifStatement);
        final PsiElement parent = elements[0].getParent();
        ifStatement = (PyIfStatement) parent.addBefore(ifStatement, elements[0]);
        final PyStatementList statementList = ifStatement.getIfPart().getStatementList();
        statementList.addRange(elements[0], elements[elements.length - 1]);
        statementList.getFirstChild().delete();
        parent.deleteChildRange(elements[0], elements[elements.length - 1]);
        return TextRange.from(statementList.getTextRange().getEndOffset(), 0);
      }

      @Override
      public String getTemplateDescription() {
        return DESCR;
      }
    };
  }
}
