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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   17:25:46
 */
public class PyParameterListFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyParameterList) {
      final PsiElement lBrace = PyUtil.getChildByFilter(psiElement, PyTokenTypes.OPEN_BRACES, 0);
      final PsiElement rBrace = PyUtil.getChildByFilter(psiElement, PyTokenTypes.CLOSE_BRACES, 0);
      if (lBrace == null || rBrace == null) {
        final Document document = editor.getDocument();
        if (lBrace == null) {
          document.insertString(psiElement.getTextRange().getStartOffset(), "(");
        }
        else {
          document.insertString(psiElement.getTextRange().getEndOffset(), ")");
        }
      }
    }
  }
}
