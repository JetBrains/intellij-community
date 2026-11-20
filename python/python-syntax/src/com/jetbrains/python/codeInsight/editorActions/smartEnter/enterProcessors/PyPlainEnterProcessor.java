// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.enterProcessors;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.ast.PyAstStatementList;
import com.jetbrains.python.ast.PyAstStatementListContainer;
import com.jetbrains.python.ast.PyAstStatementPart;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.SmartEnterUtil;
import org.jetbrains.annotations.Nullable;

public class PyPlainEnterProcessor implements EnterProcessor {
  private static @Nullable PyAstStatementList getStatementList(PsiElement psiElement, Editor editor) {
    if (psiElement instanceof PyAstStatementListContainer) {
      return ((PyAstStatementListContainer)psiElement).getStatementList();
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final PsiElement atCaret = psiElement.getContainingFile().findElementAt(caretModel.getOffset());
      final PyAstStatementPart statementPart = PsiTreeUtil.getParentOfType(atCaret, PyAstStatementPart.class);
      if (statementPart != null) {
        return statementPart.getStatementList();
      }
    }
    return null;
  }

  @Override
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    final PyAstStatementList statementList = getStatementList(psiElement, editor);
    if (statementList != null && statementList.getStatements().length == 0) {
      SmartEnterUtil.plainEnter(editor);
      //editor.getCaretModel().moveToOffset(statementList.getTextRange().getEndOffset());
      return true;
    }
    return false;
  }
}
