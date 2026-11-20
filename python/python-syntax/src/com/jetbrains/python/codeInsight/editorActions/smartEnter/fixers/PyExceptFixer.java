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
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstExceptPart;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PyExceptFixer extends PyFixer<PyAstExceptPart> {
  public PyExceptFixer() {
    super(PyAstExceptPart.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstExceptPart exceptPart) throws IncorrectOperationException {
    final PsiElement colon = PyPsiUtilsCore.getFirstChildOfType(exceptPart, PyTokenTypes.COLON);
    if (colon == null) {
      final PsiElement exceptToken = PyPsiUtilsCore.getFirstChildOfType(exceptPart, PyTokenTypes.EXCEPT_KEYWORD);
      int offset = Objects.requireNonNull(exceptToken).getTextRange().getEndOffset();
      final PyAstExpression exceptClass = exceptPart.getExceptClass();
      if (exceptClass != null) {
        offset = exceptClass.getTextRange().getEndOffset();
      }
      final PyAstExpression target = exceptPart.getTarget();
      if (target != null) {
        offset = target.getTextRange().getEndOffset();
      }
      editor.getDocument().insertString(offset, ":");
    }
  }
}
