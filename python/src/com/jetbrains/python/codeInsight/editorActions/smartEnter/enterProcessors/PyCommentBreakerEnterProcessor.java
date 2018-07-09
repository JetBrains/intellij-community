/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
