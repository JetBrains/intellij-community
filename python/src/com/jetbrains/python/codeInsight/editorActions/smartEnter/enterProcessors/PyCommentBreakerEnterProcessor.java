// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.enterProcessors;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.SmartEnterUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyCommentBreakerEnterProcessor implements EnterProcessor {
  @Override
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    if (isModified) {
      return false;
    }
    final CaretModel caretModel = editor.getCaretModel();
    PsiElement atCaret = psiElement.getContainingFile().findElementAt(caretModel.getOffset());
    if (atCaret instanceof PsiWhiteSpace) {
      atCaret = atCaret.getPrevSibling();
    }
    final PsiElement comment = PsiTreeUtil.getParentOfType(atCaret, PsiComment.class, false);
    if (comment != null) {
      SmartEnterUtil.plainEnter(editor);
      editor.getDocument().insertString(caretModel.getOffset(), "# ");
      caretModel.moveToOffset(caretModel.getOffset() + 2);
      return true;
    }
    return false;
  }
}
