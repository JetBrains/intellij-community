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
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   16:59:07
 */
public class PyFunctionFixer extends PyFixer<PyFunction> {
  public PyFunctionFixer() {
    super(PyFunction.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyFunction function)
    throws IncorrectOperationException {
    final PsiElement colon = PyUtil.getFirstChildOfType(function, PyTokenTypes.COLON);
    if (colon == null) {
      final PyParameterList parameterList = function.getParameterList();
      final Document document = editor.getDocument();
      document.insertString(parameterList.getTextRange().getEndOffset(), ":");
    }
  }
}
