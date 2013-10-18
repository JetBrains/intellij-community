package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyParenthesizedExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   17:42:08
 */
public class PyParenthesizedFixer implements PyFixer {
  public void apply(final Editor editor, final PySmartEnterProcessor processor, final PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyParenthesizedExpression) {
      final PsiElement lastChild = psiElement.getLastChild();
      if (lastChild != null && !")".equals(lastChild.getText())) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
