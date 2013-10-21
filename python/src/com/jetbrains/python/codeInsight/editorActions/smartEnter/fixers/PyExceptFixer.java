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
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   22.04.2010
 * Time:   18:13:34
 */
public class PyExceptFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyExceptPart) {
      PyExceptPart exceptPart = (PyExceptPart)psiElement;
      final PsiElement colon = PyUtil.getChildByFilter(exceptPart, TokenSet.create(PyTokenTypes.COLON), 0);
      if (colon == null) {
        int offset = PyUtil.getChildByFilter(exceptPart,
                                             TokenSet.create(PyTokenTypes.EXCEPT_KEYWORD), 0).getTextRange().getEndOffset();
        final PyExpression exceptClass = exceptPart.getExceptClass();
        if (exceptClass != null) {
          offset = exceptClass.getTextRange().getEndOffset();
        }
        final PyExpression target = exceptPart.getTarget();
        if (target != null) {
          offset = target.getTextRange().getEndOffset();
        }
        editor.getDocument().insertString(offset, ":");
      }
    }
  }
}
