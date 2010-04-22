package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   17:17:14
 */
public class PyStringLiteralFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyStringLiteralExpression) {
      final String text = psiElement.getText();
      if (StringUtil.startsWith(text, "\"\"\"")) {
        final int suffixLength = StringUtil.commonSuffixLength(text, "\"\"\"");
        if (suffixLength != 3) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"\"\"".substring(suffixLength));
        }
      }
      else if (StringUtil.startsWith(text, "\'\'\'")) {
        final int suffixLength = StringUtil.commonSuffixLength(text, "\'\'\'");
        if (suffixLength != 3) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\'\'\'".substring(suffixLength));
        }
      }
      else if (StringUtil.startsWith(text, "\"")) {
        if (!StringUtil.endsWith(text, "\"")) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\"");
        }
      }
      else if (StringUtil.startsWith(text, "\'")) {
        if (!StringUtil.endsWith(text, "\'")) {
          editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "\'");
        }
      }
    }
  }
}
