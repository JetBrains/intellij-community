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
package com.jetbrains.python.codeInsight.editorActions.smartEnter.enterProcessors;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.SmartEnterUtil;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.psi.PyStatementPart;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyPlainEnterProcessor implements EnterProcessor {
  @Nullable
  private static PyStatementList getStatementList(PsiElement psiElement, Editor editor) {
    if (psiElement instanceof PyStatementListContainer) {
      return ((PyStatementListContainer)psiElement).getStatementList();
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final PsiElement atCaret = psiElement.getContainingFile().findElementAt(caretModel.getOffset());
      final PyStatementPart statementPart = PsiTreeUtil.getParentOfType(atCaret, PyStatementPart.class);
      if (statementPart != null) {
        return statementPart.getStatementList();
      }
    }
    return null;
  }

  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    final PyStatementList statementList = getStatementList(psiElement, editor);
    if (statementList != null && statementList.getStatements().length == 0) {
      SmartEnterUtil.plainEnter(editor);
      //editor.getCaretModel().moveToOffset(statementList.getTextRange().getEndOffset());
      return true;
    }
    return false;
  }
}
