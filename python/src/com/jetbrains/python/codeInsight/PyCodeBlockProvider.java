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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.editorActions.CodeBlockProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyCodeBlockProvider implements CodeBlockProvider {
  @Nullable
  @Override
  public TextRange getCodeBlockRange(Editor editor, PsiFile psiFile) {
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement element = psiFile.findElementAt(caretOffset);
    if (element == null) {
      return null;
    }
    while (caretOffset > 0 && element instanceof PsiWhiteSpace) {
      caretOffset--;
      element = psiFile.findElementAt(caretOffset);
    }
    PyStatement statement = PsiTreeUtil.getParentOfType(element, PyStatement.class);
    if (statement != null) {
      PyStatementList statementList = PsiTreeUtil.findChildOfType(statement, PyStatementList.class);

      // if the statement above caret is not a block statement, look above for a statement list and then find the statement above
      // that statement list
      if (statementList == null) {
        statementList = PsiTreeUtil.getParentOfType(statement, PyStatementList.class);
        if (statementList != null) {
          statement = PsiTreeUtil.getParentOfType(statementList, PyStatement.class);
        }
      }
      if (statement != null) {
        // if we're in the beginning of the statement already, pressing Ctrl-[ again should move the caret one statement higher
        final int statementStart = statement.getTextRange().getStartOffset();
        int statementEnd = statement.getTextRange().getEndOffset();
        while (statementEnd > statementStart && psiFile.findElementAt(statementEnd) instanceof PsiWhiteSpace) {
          statementEnd--;
        }
        if (caretOffset == statementStart || caretOffset == statementEnd) {
          final PyStatement statementAbove = PsiTreeUtil.getParentOfType(statement, PyStatement.class);
          if (statementAbove != null) {
            if (caretOffset == statementStart) {
              return new TextRange(statementAbove.getTextRange().getStartOffset(), statementEnd);
            }
            else {
              return new TextRange(statementStart, statementAbove.getTextRange().getEndOffset());
            }
          }
        }
        return statement.getTextRange();
      }
    }
    return null;
  }
}
