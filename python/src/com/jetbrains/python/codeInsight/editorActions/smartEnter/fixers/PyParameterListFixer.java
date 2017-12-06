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
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyParameterListFixer extends PyFixer<PyParameterList> {
  public PyParameterListFixer() {
    super(PyParameterList.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyParameterList parameters)
    throws IncorrectOperationException {
    final PsiElement lBrace = PyPsiUtils.getChildByFilter(parameters, PyTokenTypes.OPEN_BRACES, 0);
    final PsiElement rBrace = PyPsiUtils.getChildByFilter(parameters, PyTokenTypes.CLOSE_BRACES, 0);
    final PyFunction pyFunction = as(parameters.getParent(), PyFunction.class);
    if (pyFunction != null && !PyFunctionFixer.isFakeFunction(pyFunction) && (lBrace == null || rBrace == null)) {
      final Document document = editor.getDocument();
      if (lBrace == null) {
        final String textToInsert = pyFunction.getNameNode() == null ? " (" : "(";
        document.insertString(parameters.getTextOffset(), textToInsert);
      }
      else if (parameters.getParameters().length == 0) {
        document.insertString(lBrace.getTextRange().getEndOffset(), ")");
      }
      else {
        document.insertString(parameters.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
