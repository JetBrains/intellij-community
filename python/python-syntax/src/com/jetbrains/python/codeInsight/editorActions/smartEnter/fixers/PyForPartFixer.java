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
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstForPart;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PyForPartFixer extends PyFixer<PyAstForPart> {
  public PyForPartFixer() {
    super(PyAstForPart.class);
  }

  @Override
  public void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PyAstForPart forPart) {
    final PsiElement colon = PyPsiUtilsCore.getFirstChildOfType(forPart, PyTokenTypes.COLON);
    final Document document = editor.getDocument();
    final PsiElement forToken = PyPsiUtilsCore.getFirstChildOfType(forPart, PyTokenTypes.FOR_KEYWORD);
    if (colon == null) {
      String textToInsert = ":";
      PsiElement sourceOrTarget = forPart.getSource();
      PsiElement positionToInsert = sourceOrTarget;
      if (sourceOrTarget == null) {
        sourceOrTarget = forPart.getTarget();
        final PsiElement inToken = PyPsiUtilsCore.getFirstChildOfType(forPart, PyTokenTypes.IN_KEYWORD);
        if (inToken == null) {
          if (sourceOrTarget == null) {
            positionToInsert = Objects.requireNonNull(forToken);
            textToInsert = "  in :";
            processor.registerUnresolvedError(positionToInsert.getTextRange().getEndOffset() + 1);
          }
          else {
            positionToInsert = sourceOrTarget;
            textToInsert = " in :";
            processor.registerUnresolvedError(positionToInsert.getTextRange().getEndOffset() + 4);
          }
        }
        else {
          positionToInsert = inToken;
          textToInsert = " :";
          processor.registerUnresolvedError(positionToInsert.getTextRange().getEndOffset() + 1);
        }
      }
      document.insertString(positionToInsert.getTextRange().getEndOffset(), textToInsert);
    }
  }
}
