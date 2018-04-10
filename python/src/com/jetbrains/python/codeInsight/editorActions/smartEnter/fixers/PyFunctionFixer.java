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
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class PyFunctionFixer extends PyFixer<PyFunction> {
  public PyFunctionFixer() {
    super(PyFunction.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyFunction function)
    throws IncorrectOperationException {
    final PsiElement colon = PyPsiUtils.getFirstChildOfType(function, PyTokenTypes.COLON);
    if (!isFakeFunction(function) && colon == null) {
      final PyParameterList parameterList = function.getParameterList();
      if (function.getNameNode() == null) {
        processor.registerUnresolvedError(parameterList.getTextOffset());
      }
      final int colonOffset;
      final PyAnnotation annotation = function.getAnnotation();
      if (annotation != null) {
        colonOffset = annotation.getTextRange().getEndOffset();
      }
      else {
        colonOffset = parameterList.getTextRange().getEndOffset();
      }
      editor.getDocument().insertString(colonOffset, ":");
    }
  }

  /**
   * Python parser can create empty function element without header and body solely to enclose {@link com.jetbrains.python.psi.PyDecoratorList}.
   * Attempting to operate in the context of such "fake" function definition may lead to various kinds of malformed code and we want to
   * avoid it.
   *
   * @return whether it's more or less proper function definition, i.e. it contains at least {@code def} keyword
   */
  static boolean isFakeFunction(@NotNull PyFunction function) {
    return function.getNode().findChildByType(PyTokenTypes.DEF_KEYWORD) == null;
  }
}
