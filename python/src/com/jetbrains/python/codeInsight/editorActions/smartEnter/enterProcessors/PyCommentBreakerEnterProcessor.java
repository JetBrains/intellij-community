package com.jetbrains.python.codeInsight.editorActions.smartEnter.enterProcessors;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.SmartEnterUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   18:39:07
 */
public class PyCommentBreakerEnterProcessor implements EnterProcessor {
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    if (isModified) {
      return false;
    }
    final CaretModel caretModel = editor.getCaretModel();
    final PsiElement atCaret = psiElement.getContainingFile().findElementAt(caretModel.getOffset());
    final PsiElement comment = PsiTreeUtil.getParentOfType(atCaret, PsiComment.class, false);
    if (comment != null) {
      SmartEnterUtil.plainEnter(editor);
      editor.getDocument().insertString(caretModel.getOffset(), "#");
      caretModel.moveToOffset(caretModel.getOffset() + 1);
      return true;
    }
    return false;
  }
}
