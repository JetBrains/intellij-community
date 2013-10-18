package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   16:59:07
 */
public class PyFunctionFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyFunction) {
      final PsiElement colon = PyUtil.getChildByFilter(psiElement, TokenSet.create(PyTokenTypes.COLON), 0);
      if (colon == null) {
        final PyFunction function = (PyFunction)psiElement;
        final PyParameterList parameterList = function.getParameterList();
        final Document document = editor.getDocument();
        document.insertString(parameterList.getTextRange().getEndOffset(), ":");
      }
    }
  }
}
