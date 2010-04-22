package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyTryPart;
import com.jetbrains.python.psi.PyUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   14:25:20
 */
public class PyUnconditionalStatementPartFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (PyUtil.instanceOf(psiElement, PyElsePart.class, PyTryPart.class, PyFinallyPart.class)) {
      final PsiElement colon = PyUtil.getChildByFilter(psiElement, TokenSet.create(PyTokenTypes.COLON), 0);
      if (colon == null) {
        final PsiElement keywordToken = PyUtil.getChildByFilter(psiElement,
                                                                TokenSet.create(PyTokenTypes.ELSE_KEYWORD, PyTokenTypes.TRY_KEYWORD,
                                                                                PyTokenTypes.FINALLY_KEYWORD),
                                                                0);
        editor.getDocument().insertString(keywordToken.getTextRange().getEndOffset(), ":");
      }
    }
  }
}
