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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstArgumentList;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.ast.PyAstDecorator;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import org.jetbrains.annotations.NotNull;

public class PyArgumentListFixer extends PyFixer<PyAstArgumentList> {
  public PyArgumentListFixer() {
    super(PyAstArgumentList.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstArgumentList arguments) throws IncorrectOperationException {
    final PsiElement rBrace = PyPsiUtilsCore.getChildByFilter(arguments, PyTokenTypes.CLOSE_BRACES, 0);
    if (arguments.getParent() instanceof PyAstClass || arguments.getParent() instanceof PyAstDecorator) {
      final PsiElement lBrace = PyPsiUtilsCore.getChildByFilter(arguments, PyTokenTypes.OPEN_BRACES, 0);
      if (lBrace != null && rBrace == null) {
        final Document document = editor.getDocument();
        document.insertString(arguments.getTextRange().getEndOffset(), ")");
      }
    }
    else {
      if (rBrace == null) {
        final Document document = editor.getDocument();
        document.insertString(arguments.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
