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
import com.jetbrains.python.ast.PyAstAnnotation;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstParameterList;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import org.jetbrains.annotations.NotNull;

public class PyFunctionFixer extends PyFixer<PyAstFunction> {
  public PyFunctionFixer() {
    super(PyAstFunction.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstFunction function)
    throws IncorrectOperationException {
    final PsiElement colon = PyPsiUtilsCore.getFirstChildOfType(function, PyTokenTypes.COLON);
    if (!isFakeFunction(function) && colon == null) {
      final PyAstParameterList parameterList = function.getParameterList();
      if (function.getNameNode() == null) {
        processor.registerUnresolvedError(parameterList.getTextOffset());
      }
      final int colonOffset;
      final PyAstAnnotation annotation = function.getAnnotation();
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
   * Python parser can create empty function element without header and body solely to enclose {@link com.jetbrains.python.ast.PyAstDecoratorList}.
   * Attempting to operate in the context of such "fake" function definition may lead to various kinds of malformed code and we want to
   * avoid it.
   *
   * @return whether it's more or less proper function definition, i.e. it contains at least {@code def} keyword
   */
  static boolean isFakeFunction(@NotNull PyAstFunction function) {
    return function.getNode().findChildByType(PyTokenTypes.DEF_KEYWORD) == null;
  }
}
