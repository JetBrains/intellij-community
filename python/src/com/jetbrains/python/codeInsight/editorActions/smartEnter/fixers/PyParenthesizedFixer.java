/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.jetbrains.python.psi.PyParenthesizedExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   17:42:08
 */
public class PyParenthesizedFixer implements PyFixer {
  public void apply(final Editor editor, final PySmartEnterProcessor processor, final PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyParenthesizedExpression) {
      final PsiElement lastChild = psiElement.getLastChild();
      if (lastChild != null && !")".equals(lastChild.getText())) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
