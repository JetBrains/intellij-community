package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   18:41:08
 */
public class PyClassFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyClass) {
      final PsiElement colon = PyUtil.getChildByFilter(psiElement, TokenSet.create(PyTokenTypes.COLON), 0);
      if (colon == null) {
        final PyClass aClass = (PyClass)psiElement;
        final PyArgumentList argList = PsiTreeUtil.getChildOfType(aClass, PyArgumentList.class);
        int offset = argList.getTextRange().getEndOffset();
        String textToInsert = ":";
        if (aClass.getNameNode() == null) {
          processor.registerUnresolvedError(argList.getTextRange().getEndOffset() + 1);
          textToInsert = " :";
        }
        editor.getDocument().insertString(offset, textToInsert);
      }
    }
  }
}
