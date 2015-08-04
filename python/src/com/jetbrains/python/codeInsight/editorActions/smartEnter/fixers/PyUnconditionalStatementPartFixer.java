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
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   14:25:20
 */
public class PyUnconditionalStatementPartFixer extends PyFixer<PyElement> {
  public PyUnconditionalStatementPartFixer() {
    super(PyElement.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyElement psiElement)
    throws IncorrectOperationException {
    if (PyUtil.instanceOf(psiElement, PyElsePart.class, PyTryPart.class, PyFinallyPart.class)) {
      final PsiElement colon = PyPsiUtils.getFirstChildOfType(psiElement, PyTokenTypes.COLON);
      if (colon == null) {
        final TokenSet keywords = TokenSet.create(PyTokenTypes.ELSE_KEYWORD, PyTokenTypes.TRY_KEYWORD, PyTokenTypes.FINALLY_KEYWORD);
        final PsiElement keywordToken = PyPsiUtils.getChildByFilter(psiElement, keywords, 0);
        editor.getDocument().insertString(sure(keywordToken).getTextRange().getEndOffset(), ":");
      }
    }
  }
}
