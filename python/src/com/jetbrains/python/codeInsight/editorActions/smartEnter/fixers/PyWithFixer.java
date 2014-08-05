/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * @author Mikhail Golubev
 */
public class PyWithFixer extends PyFixer<PyWithStatement> {
  public PyWithFixer() {
    super(PyWithStatement.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyWithStatement withStatement) throws IncorrectOperationException {
    final PsiElement colonToken = PyUtil.getFirstChildOfType(withStatement, PyTokenTypes.COLON);
    final PsiElement withToken = PyUtil.getFirstChildOfType(withStatement, PyTokenTypes.WITH_KEYWORD);
    final Document document = editor.getDocument();
    if (colonToken == null) {
      int insertAt = sure(withToken).getTextRange().getEndOffset();
      String textToInsert = ":";
      final PyWithItem[] withItems = withStatement.getWithItems();
      final PyWithItem lastItem = withItems.length != 0 ? withItems[withItems.length - 1] : null;
      if (lastItem == null || lastItem.getExpression() == null) {
        textToInsert = " :";
        processor.registerUnresolvedError(insertAt + 1);
      }
      else {
        final PyExpression expression = lastItem.getExpression();
        insertAt = expression.getTextRange().getEndOffset();
        final PsiElement asToken = PyUtil.getFirstChildOfType(lastItem, PyTokenTypes.AS_KEYWORD);
        if (asToken != null) {
          insertAt = asToken.getTextRange().getEndOffset();
          final PyExpression target = lastItem.getTarget();
          if (target != null) {
            insertAt = target.getTextRange().getEndOffset();
          }
          else {
            textToInsert = " :";
            processor.registerUnresolvedError(insertAt + 1);
          }
        }
      }
      document.insertString(insertAt, textToInsert);
    }
  }
}
