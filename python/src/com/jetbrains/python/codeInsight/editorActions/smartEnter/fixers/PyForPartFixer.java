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
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   16.04.2010
 * Time:   16:03:43
 */
public class PyForPartFixer implements PyFixer {
  public void apply(Editor editor, PySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PyForPart) {
      final PyForPart forPart = (PyForPart)psiElement;
      final PsiElement colon = PyUtil.getChildByFilter(psiElement, TokenSet.create(PyTokenTypes.COLON), 0);
      final Document document = editor.getDocument();
      final PsiElement forToken = PyUtil.getChildByFilter(forPart,
                                                          TokenSet.create(PyTokenTypes.FOR_KEYWORD), 0);
      if (colon == null) {
        String textToInsert = ":";
        PsiElement sourceOrTarget = forPart.getSource();
        PsiElement positionToInsert = sourceOrTarget;
        if (sourceOrTarget == null) {
          sourceOrTarget = forPart.getTarget();
          final PsiElement inToken = PyUtil.getChildByFilter(forPart, TokenSet.create(PyTokenTypes.IN_KEYWORD), 0);
          if (inToken == null) {
            if (sourceOrTarget == null) {
              positionToInsert = forToken;
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
}
