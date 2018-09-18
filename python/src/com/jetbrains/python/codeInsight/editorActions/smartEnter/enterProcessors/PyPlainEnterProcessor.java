// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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
