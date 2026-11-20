// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstArgumentList;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.ast.impl.PyUtilCore.sure;

public class PyClassFixer extends PyFixer<PyAstClass> {
  public PyClassFixer() {
    super(PyAstClass.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstClass pyClass) throws IncorrectOperationException {
    final PsiElement colon = PyPsiUtilsCore.getFirstChildOfType(pyClass, PyTokenTypes.COLON);
    if (colon == null) {
      final PyAstArgumentList argList = PsiTreeUtil.getChildOfType(pyClass, PyAstArgumentList.class);
      final int colonOffset = sure(argList).getTextRange().getEndOffset();
      String textToInsert = ":";
      if (pyClass.getNameNode() == null) {
        int newCaretOffset = argList.getTextOffset();
        if (argList.getTextLength() == 0) {
          newCaretOffset += 1;
          textToInsert = " :";
        }
        processor.registerUnresolvedError(newCaretOffset);
      }
      editor.getDocument().insertString(colonOffset, textToInsert);
    }
  }
}
