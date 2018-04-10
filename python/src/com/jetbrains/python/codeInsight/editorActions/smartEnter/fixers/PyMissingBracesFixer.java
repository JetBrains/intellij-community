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
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyMissingBracesFixer extends PyFixer<PyElement> {
  public PyMissingBracesFixer() {
    super(PyElement.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyElement psiElement)
    throws IncorrectOperationException {
    if (psiElement instanceof PySetLiteralExpression || psiElement instanceof PyDictLiteralExpression) {
      final PsiElement lastChild = PyPsiUtils.getPrevNonCommentSibling(psiElement.getLastChild(), false);
      if (lastChild != null && !"}".equals(lastChild.getText())) {
        editor.getDocument().insertString(lastChild.getTextRange().getEndOffset(), "}");
      }
    }
    else if (psiElement instanceof PyListLiteralExpression ||
             psiElement instanceof PySliceExpression ||
             psiElement instanceof PySubscriptionExpression) {
      final PsiElement lastChild = PyPsiUtils.getPrevNonCommentSibling(psiElement.getLastChild(), false);
      if (lastChild != null && !"]".equals(lastChild.getText())) {
        editor.getDocument().insertString(lastChild.getTextRange().getEndOffset(), "]");
      }
    }
  }
}
