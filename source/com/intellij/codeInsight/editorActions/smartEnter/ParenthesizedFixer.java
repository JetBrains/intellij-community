/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.util.IncorrectOperationException;

public class ParenthesizedFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiParenthesizedExpression) {
      String text = psiElement.getText();
      if (!StringUtil.endsWithChar(text, ')')) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), ")");
      }
    }
  }
}