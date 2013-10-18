package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   17:55:46
 */
public class PyMissingBracesFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PySetLiteralExpression || psiElement instanceof PyDictLiteralExpression) {
      PsiElement lastChild = PyUtil.getFirstNonCommentBefore(psiElement.getLastChild());
      if (lastChild != null && !"}".equals(lastChild.getText())) {
        editor.getDocument().insertString(lastChild.getTextRange().getEndOffset(), "}");
      }
    }
    else if (psiElement instanceof PyListLiteralExpression ||
             psiElement instanceof PySliceExpression ||
             psiElement instanceof PySubscriptionExpression) {
      PsiElement lastChild = PyUtil.getFirstNonCommentBefore(psiElement.getLastChild());
      if (lastChild != null && !"]".equals(lastChild.getText())) {
        editor.getDocument().insertString(lastChild.getTextRange().getEndOffset(), "]");
      }
    }
  }
}
