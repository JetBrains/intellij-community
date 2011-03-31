package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyUtil;

/**
 * @author Alexey.Ivanov
 */
public class PyArgumentListFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyArgumentList) {
      final PsiElement rBrace = PyUtil.getChildByFilter(psiElement, PyTokenTypes.CLOSE_BRACES, 0);
      if (psiElement.getParent() instanceof PyClass || psiElement.getParent() instanceof PyDecorator) {
        final PsiElement lBrace = PyUtil.getChildByFilter(psiElement, PyTokenTypes.OPEN_BRACES, 0);
        if (lBrace != null && rBrace == null) {
          final Document document = editor.getDocument();
          document.insertString(psiElement.getTextRange().getEndOffset(), ")");
        }
      }
      else {
        if (rBrace == null) {
          final Document document = editor.getDocument();
          document.insertString(psiElement.getTextRange().getEndOffset(), ")");
        }
      }
    }
  }
}
