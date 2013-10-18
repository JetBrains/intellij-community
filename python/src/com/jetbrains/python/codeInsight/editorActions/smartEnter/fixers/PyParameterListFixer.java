package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   17:25:46
 */
public class PyParameterListFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyParameterList) {
      final PsiElement lBrace = PyUtil.getChildByFilter(psiElement, PyTokenTypes.OPEN_BRACES, 0);
      final PsiElement rBrace = PyUtil.getChildByFilter(psiElement, PyTokenTypes.CLOSE_BRACES, 0);
      if (lBrace == null || rBrace == null) {
        final Document document = editor.getDocument();
        if (lBrace == null) {
          document.insertString(psiElement.getTextRange().getStartOffset(), "(");
        }
        else {
          document.insertString(psiElement.getTextRange().getEndOffset(), ")");
        }
      }
    }
  }
}
